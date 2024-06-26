package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.author = body.author?.let { EntityID(it, AuthorTable) }
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse =
        withContext(Dispatchers.IO) {
            transaction {
                val query = BudgetTable
                    .join(AuthorTable, JoinType.INNER, BudgetTable.author, AuthorTable.id)
                    .select { BudgetTable.year eq param.year }
                    .limit(param.limit, param.offset)
                if (!param.authorNameFilter.isNullOrBlank()) {
                    query.andWhere {
                        AuthorTable.fullName.lowerCase() like
                                "%${param.authorNameFilter.toLowerCase()}%"
                    }
                }

                val total = query.count()
                val data = BudgetEntity.wrapRows(query).map { it.toResponse() }


                val sumByType =
                    data.groupBy { it.type.name }.mapValues { it.value.sumOf { v -> v.amount } }

                return@transaction BudgetYearStatsResponse(
                    total = total,
                    totalByType = sumByType,
                    items = data,
                )
            }
        }
}