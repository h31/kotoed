package org.jetbrains.research.kotoed.api

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.launch
import org.jetbrains.research.kotoed.data.api.*
import org.jetbrains.research.kotoed.data.db.ComplexDatabaseQuery
import org.jetbrains.research.kotoed.data.db.setPageForQuery
import org.jetbrains.research.kotoed.data.db.textSearch
import org.jetbrains.research.kotoed.data.notification.NotificationType
import org.jetbrains.research.kotoed.database.Tables
import org.jetbrains.research.kotoed.database.enums.SubmissionCommentState
import org.jetbrains.research.kotoed.database.enums.SubmissionState
import org.jetbrains.research.kotoed.database.tables.records.NotificationRecord
import org.jetbrains.research.kotoed.database.tables.records.SubmissionCommentRecord
import org.jetbrains.research.kotoed.database.tables.records.SubmissionRecord
import org.jetbrains.research.kotoed.db.condition.lang.formatToQuery
import org.jetbrains.research.kotoed.eventbus.Address
import org.jetbrains.research.kotoed.util.*
import org.jetbrains.research.kotoed.util.database.toJson
import org.jetbrains.research.kotoed.util.database.toRecord

@AutoDeployable
class SubmissionCommentVerticle : AbstractKotoedVerticle(), Loggable {

    private suspend fun queryCommentsWithAuthorAndSubmission(record: SubmissionCommentRecord) =
        dbQueryAsync(
                ComplexDatabaseQuery(find = record)
                        .join(Tables.DENIZEN, field = Tables.SUBMISSION_COMMENT.AUTHOR_ID.name)
                        .join(field = Tables.SUBMISSION_COMMENT.SUBMISSION_ID.name,
                              query = ComplexDatabaseQuery(Tables.SUBMISSION)
                                      .join(Tables.PROJECT, field = Tables.SUBMISSION.PROJECT_ID.name)
                        )
        )

    private suspend fun notifyCreated(record: SubmissionCommentRecord) {
        val rich = queryCommentsWithAuthorAndSubmission(
                SubmissionCommentRecord().apply{ id = record.id }
        ).first()

        val thread: List<SubmissionCommentRecord> = dbFindAsync(SubmissionCommentRecord().apply {
            submissionId = record.submissionId
            sourcefile = record.sourcefile
            sourceline = record.sourceline
        })

        val submissionOwner = rich["submission", "project", "denizenId"] as? Int

        if(record.authorId != submissionOwner) {
            createNotification (
                    NotificationRecord().apply {
                        denizenId = submissionOwner
                        type = NotificationType.NEW_COMMENT.toString()
                        body = rich
                    }
            )
        }

        thread.groupBy { it.authorId }
                .filterKeys { it != record.authorId }
                .forEach { (author, _) ->
                    createNotification (
                            NotificationRecord().apply {
                                denizenId = author
                                type = NotificationType.COMMENT_REPLIED_TO.toString()
                                body = rich
                            }
                    )
                }
    }

    @JsonableEventBusConsumerFor(Address.Api.Submission.Comment.Create)
    suspend fun handleCreate(comment: SubmissionCommentRecord): DbRecordWrapper<SubmissionCommentRecord> {
        comment.id = null
        comment.originalSubmissionId = comment.submissionId // NOTE: this is unconditional for a reason
        comment.state = SubmissionCommentState.open

        val submission: SubmissionRecord = fetchByIdAsync(SubmissionRecord().table, comment.submissionId)
        val res = when (submission.state) {
            SubmissionState.open -> dbCreateAsync(comment)
            SubmissionState.obsolete -> {
                log.warn("Comment request for an obsolete submission received: " +
                        "Submission id = ${submission.id}")
                val wrappedSuccessor: DbRecordWrapper<SubmissionRecord> =
                        sendJsonableAsync(
                                Address.Api.Submission.Last,
                                submission
                        )
                if(wrappedSuccessor.verificationData.status == VerificationStatus.Invalid) {
                    throw IllegalArgumentException("Applying comment to an incorrect submission")
                }
                val successor: SubmissionRecord = wrappedSuccessor.record
                log.trace("Newer submission found: id = ${successor.id}")
                comment.submissionId = successor.id
                // recursive message
                if(wrappedSuccessor.verificationData.status != VerificationStatus.Processed) {
                    vertx.delayAsync(1000)
                }
                // TODO: translate location between revisions
                return handleCreate(comment)
            }
            else -> throw IllegalArgumentException("Applying comment to an incorrect or incomplete submission")
        }

        val ret = DbRecordWrapper(res, VerificationData.Processed)

        launch(LogExceptions() + currentCoroutineName()) {
            notifyCreated(res)
        }

        return ret
    }

    @JsonableEventBusConsumerFor(Address.Api.Submission.Comment.Read)
    suspend fun handleRead(comment: SubmissionCommentRecord) =
            DbRecordWrapper(dbFetchAsync(comment), VerificationData.Processed)

    private suspend fun notifyStateChanged(before: SubmissionCommentRecord, after: SubmissionCommentRecord) {
        val rich = queryCommentsWithAuthorAndSubmission(SubmissionCommentRecord().apply { id = after.id }).first()

        val submissionOwner = rich["submission", "project", "denizenId"] as? Int

        val notificationType = when(before.state to after.state) {
            (SubmissionCommentState.open to SubmissionCommentState.closed) ->
                    NotificationType.COMMENT_CLOSED.toString()
            (SubmissionCommentState.closed to SubmissionCommentState.open) ->
                    NotificationType.COMMENT_REOPENED.toString()
            else -> return // wt*
        }

        if(after.authorId != submissionOwner) {
            createNotification (
                    NotificationRecord().apply {
                        denizenId = submissionOwner
                        type = notificationType
                        body = rich
                    }
            )
        }

    }

    @JsonableEventBusConsumerFor(Address.Api.Submission.Comment.Update)
    suspend fun handleUpdate(comment: SubmissionCommentRecord): DbRecordWrapper<SubmissionCommentRecord> {
        val existing = fetchByIdAsync(Tables.SUBMISSION_COMMENT, comment.id)
        existing.id ?: throw NotFound("Comment ${comment.id} not found")
        if(comment.text == null && existing.text != null) comment.text = existing.text
        if(comment.state == null) comment.state = existing.state
        comment.submissionId         = existing.submissionId
        comment.datetime             = existing.datetime
        comment.sourcefile           = existing.sourcefile
        comment.sourceline           = existing.sourceline
        comment.authorId             = existing.authorId
        comment.persistentCommentId  = existing.persistentCommentId

        val res = DbRecordWrapper(dbUpdateAsync(comment), VerificationData.Processed)

        if(comment.state != existing.state) {
            launch(LogExceptions() + currentCoroutineName()) {
                notifyStateChanged(existing, comment)
            }
        }

        return res
    }

    @JsonableEventBusConsumerFor(Address.Api.Submission.Comment.Search)
    suspend fun handleSearch(query: SearchQuery): JsonArray {
        val q = ComplexDatabaseQuery("submission_comment_text_search")
                .join(table = "denizen", field = "author_id")
                .join("submission")
                .textSearch(query.text)
                .filter("submission.state != %s".formatToQuery(SubmissionState.obsolete))
                .setPageForQuery(query)

        val req: List<JsonObject> = sendJsonableCollectAsync(Address.DB.query("submission_comment_text_search"), q)

        return req.map {
            it.toRecord<SubmissionCommentRecord>().toJson().apply {
                put("denizen_id", it["author", "denizen_id"])
            }
        }.let(::JsonArray)
    }

    @JsonableEventBusConsumerFor(Address.Api.Submission.Comment.SearchCount)
    suspend fun handleSearchCount(query: SearchQuery): CountResponse {
        val q = ComplexDatabaseQuery("submission_comment_text_search")
                .join(table = "denizen", field = "author_id")
                .join("submission")
                .textSearch(query.text)
                .filter("submission.state != %s".formatToQuery(SubmissionState.obsolete))

        return sendJsonableAsync(Address.DB.count("submission_comment_text_search"), q)
    }
}
