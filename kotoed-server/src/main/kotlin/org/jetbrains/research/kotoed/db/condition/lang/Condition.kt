package org.jetbrains.research.kotoed.db.condition.lang

import org.jetbrains.research.kotoed.util.uncheckedCast
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Table
import org.jooq.impl.DSL

private fun<T> convertPrimitive(e: Expression, tables: (String) -> Table<*>): Field<T> = when(e){
    is Path -> tables(e.path.dropLast(1).joinToString(".")).field(e.path.last()).uncheckedCast()
    is IntConstant -> DSL.inline(e.value).uncheckedCast()
    is StringConstant -> DSL.inline(e.value).uncheckedCast()
    is NullConstant -> DSL.field("NULL").uncheckedCast()
    else -> error("convertPrimitive() is for primitives only!")
}

private fun<T> convertCompareExpression(cmp: CompareExpression, tables: (String) -> Table<*>) = when(cmp.op) {
    CompareOp.EQ -> convertPrimitive<T>(cmp.lhv, tables).eq(convertPrimitive<T>(cmp.rhv, tables))
    CompareOp.NE -> convertPrimitive<T>(cmp.lhv, tables).ne(convertPrimitive<T>(cmp.rhv, tables))
    CompareOp.GT -> convertPrimitive<T>(cmp.lhv, tables).gt(convertPrimitive<T>(cmp.rhv, tables))
    CompareOp.GE -> convertPrimitive<T>(cmp.lhv, tables).ge(convertPrimitive<T>(cmp.rhv, tables))
    CompareOp.LT -> convertPrimitive<T>(cmp.lhv, tables).lt(convertPrimitive<T>(cmp.rhv, tables))
    CompareOp.LE -> convertPrimitive<T>(cmp.lhv, tables).le(convertPrimitive<T>(cmp.rhv, tables))
}

private fun convertBinaryExpression(bin: BinaryExpression, tables: (String) -> Table<*>) = when(bin.op) {
    BinaryOp.AND -> convertAst(bin.lhv, tables).and(convertAst(bin.rhv, tables))
    BinaryOp.OR -> convertAst(bin.lhv, tables).or(convertAst(bin.rhv, tables))
}

private fun convertAst(e: Expression, tables: (String) -> Table<*>): Condition = when(e) {
    is CompareExpression -> convertCompareExpression<Any>(e, tables)
    is NotExpression -> DSL.not(convertAst(e.rhv, tables))
    is BinaryExpression -> convertBinaryExpression(e, tables)
    else -> DSL.condition(convertPrimitive<Any>(e, tables).uncheckedCast<Field<Boolean>>())
}

fun parseCondition(input: String, tables: (String) -> Table<*>): Condition {
    val e = ExpressionParsers.root.parse(input)
    return convertAst(e, tables)
}