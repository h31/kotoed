import {SubmissionDetailsProps} from "./components/SubmissionDetails";
import {Action} from "redux";
import {isType} from "typescript-fsa";
import {
    availableTagsFetch,
    commentsTotalFetch,
    historyFetch,
    permissionsFetch,
    submissionFetch,
    submissionTagAdd,
    submissionTagDelete,
    tagListFetch
} from "./actions";
import {isNullOrUndefined} from "util";

const initialState: SubmissionDetailsProps = {
    history: [],
    permissions: {
        resubmit: false,
        changeState: false,
        clean: false,
        tags: false
    },
    loading: true,
    submission: {
        record: {
            id: 0,
            state: "invalid",
            datetime: 0,
            projectId: 0,
            revision: "",
            project: {
                denizenId: 0,
                courseId: 0,
                repoType: "git",
                repoUrl: "",
                id: 0,
                name: "",
                denizen: {
                    id: 0,
                    denizenId: "",
                    email: ""
                }
            }
        },
        verificationData: {
            status: "Invalid"
        }
    },
    comments: {
        open: 0,
        closed: 0
    },
    tags: [],
    availableTags: [],
    tagsDisabled: false
};

export function reducer(state: SubmissionDetailsProps = initialState, action: Action): SubmissionDetailsProps {
    if (isType(action, submissionFetch.done)) {
        return {...state, submission: action.payload.result, loading: false}
    } else if (isType(action, permissionsFetch.done)) {
        return {...state, permissions: action.payload.result}
    } else if (isType(action, historyFetch.done)) {
        let newHistory = [...state.history, ...action.payload.result];
        return {...state, history: newHistory}
    } else if (isType(action, commentsTotalFetch.done)) {
        return {...state, comments: action.payload.result}
    } else if (isType(action, tagListFetch.started) ||
            isType(action, availableTagsFetch.started) ||
            isType(action, submissionTagAdd.started) ||
            isType(action, submissionTagDelete.started)
    ) {
        return {...state, tagsDisabled: true}
    } else if (isType(action, tagListFetch.done)) {
        return {...state, tags: action.payload.result, tagsDisabled: false}
    } else if (isType(action, availableTagsFetch.done)) {
        return {...state, availableTags: action.payload.result, tagsDisabled: false}
    } else if (isType(action, submissionTagAdd.done)) {
        const tag = state.availableTags
            .find(tag => action.payload.result === tag.id);
        if (isNullOrUndefined(tag))
            return state;
        else
            return {...state, tags: state.tags.concat([tag]), tagsDisabled: false}
    } else if (isType(action, submissionTagDelete.done)) {
        const tag = state.availableTags
            .find(tag => action.payload.result === tag.id);
        if (isNullOrUndefined(tag))
            return state;
        else
            return {...state, tags: state.tags.filter(t => tag.name !== t.name), tagsDisabled: false}
    }
    return state;
}
