package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestampMillis DESC, id DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE timestampMillis BETWEEN :startMillis AND :endMillis ORDER BY timestampMillis DESC")
    suspend fun getExpensesInRange(startMillis: Long, endMillis: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY timestampMillis DESC LIMIT 200")
    suspend fun getRecentExpensesForHistory(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE isDemo = 0 ORDER BY timestampMillis DESC")
    suspend fun getNonDemoExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses WHERE isDemo = 1")
    suspend fun countDemoExpenses(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Query("UPDATE expenses SET accountName = :newName WHERE LOWER(TRIM(accountName)) = LOWER(TRIM(:oldName))")
    suspend fun renameAccountExpenses(oldName: String, newName: String)

    @Query("UPDATE expenses SET accountName = '' WHERE LOWER(TRIM(accountName)) = LOWER(TRIM(:accountName))")
    suspend fun clearAccountFromExpenses(accountName: String)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)

    @Query("DELETE FROM expenses WHERE isDemo = 1")
    suspend fun deleteDemoExpenses()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY createdAtMillis ASC, id ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY createdAtMillis ASC, id ASC")
    suspend fun getAllAccountsOnce(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getAccountByNameIgnoreCase(name: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: Long)

    @Query("DELETE FROM accounts WHERE isDemo = 1")
    suspend fun deleteDemoAccounts()
}

@Dao
interface CategoryBudgetDao {
    @Query("SELECT * FROM category_budgets")
    fun observeAllCategoryBudgets(): Flow<List<CategoryBudgetEntity>>

    @Query("SELECT * FROM category_budgets")
    suspend fun getAllCategoryBudgetsOnce(): List<CategoryBudgetEntity>

    @Query("SELECT * FROM category_budgets WHERE category = :category LIMIT 1")
    suspend fun getBudgetForCategory(category: String): CategoryBudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: CategoryBudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudgets(budgets: List<CategoryBudgetEntity>)

    @Query("DELETE FROM category_budgets WHERE category = :category")
    suspend fun deleteBudget(category: String)

    @Query("UPDATE category_budgets SET alert80SentMonth = :monthKey WHERE category = :category")
    suspend fun markAlert80Sent(category: String, monthKey: String)

    @Query("UPDATE category_budgets SET alert100SentMonth = :monthKey WHERE category = :category")
    suspend fun markAlert100Sent(category: String, monthKey: String)
}

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM financial_commitments ORDER BY isActive DESC, dueDayOfMonth ASC, createdAtMillis ASC")
    fun observeAllCommitments(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM financial_commitments")
    suspend fun getAllCommitmentsOnce(): List<CommitmentEntity>

    @Query("SELECT * FROM financial_commitments WHERE id = :id LIMIT 1")
    suspend fun getCommitmentById(id: Long): CommitmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommitment(commitment: CommitmentEntity): Long

    @Update
    suspend fun updateCommitment(commitment: CommitmentEntity)

    @Query("UPDATE financial_commitments SET accountName = :newName WHERE LOWER(TRIM(accountName)) = LOWER(TRIM(:oldName))")
    suspend fun renameAccountCommitments(oldName: String, newName: String)

    @Query("UPDATE financial_commitments SET accountName = '' WHERE LOWER(TRIM(accountName)) = LOWER(TRIM(:accountName))")
    suspend fun clearAccountFromCommitments(accountName: String)

    @Query("DELETE FROM financial_commitments WHERE id = :id")
    suspend fun deleteCommitmentById(id: Long)
}

@Dao
interface DetectedTransactionDao {
    @Query("SELECT * FROM detected_transactions WHERE status = 'PENDING' ORDER BY receivedAtMillis DESC, id DESC")
    fun observePendingDetected(): Flow<List<DetectedTransactionEntity>>

    @Query("SELECT * FROM detected_transactions ORDER BY receivedAtMillis DESC LIMIT 50")
    suspend fun getRecentDetectedOnce(): List<DetectedTransactionEntity>

    @Query("SELECT COUNT(*) FROM detected_transactions WHERE fingerprint = :fingerprint AND receivedAtMillis >= :sinceMillis")
    suspend fun countByFingerprintSince(fingerprint: String, sinceMillis: Long): Int

    @Query("SELECT * FROM detected_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DetectedTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetected(item: DetectedTransactionEntity): Long

    @Query("UPDATE detected_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM detected_transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM detected_transactions WHERE status = 'DISMISSED'")
    suspend fun clearDismissed()
}

@Dao
interface AccountTransferDao {
    @Query("SELECT * FROM account_transfers ORDER BY timestampMillis DESC, id DESC")
    fun observeAllTransfers(): Flow<List<AccountTransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: AccountTransferEntity): Long

    @Update
    suspend fun updateTransfer(transfer: AccountTransferEntity)

    @Query("UPDATE account_transfers SET fromAccountName = :newName WHERE LOWER(TRIM(fromAccountName)) = LOWER(TRIM(:oldName))")
    suspend fun renameFromAccount(oldName: String, newName: String)

    @Query("UPDATE account_transfers SET toAccountName = :newName WHERE LOWER(TRIM(toAccountName)) = LOWER(TRIM(:oldName))")
    suspend fun renameToAccount(oldName: String, newName: String)

    @Query("DELETE FROM account_transfers WHERE id = :id")
    suspend fun deleteTransferById(id: Long)

    @Query("DELETE FROM account_transfers WHERE isDemo = 1")
    suspend fun deleteDemoTransfers()
}

@Dao
interface PointsAccountDao {
    @Query("SELECT * FROM points_accounts ORDER BY updatedAtMillis DESC, id ASC")
    fun observeAllPointsAccounts(): Flow<List<PointsAccountEntity>>

    @Query("SELECT * FROM points_accounts ORDER BY updatedAtMillis DESC, id ASC")
    suspend fun getAllPointsAccountsOnce(): List<PointsAccountEntity>

    @Query("SELECT * FROM points_accounts WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getPointsAccountByNameIgnoreCase(name: String): PointsAccountEntity?

    @Query("SELECT * FROM points_accounts WHERE id = :id LIMIT 1")
    suspend fun getPointsAccountById(id: Long): PointsAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPointsAccount(account: PointsAccountEntity): Long

    @Update
    suspend fun updatePointsAccount(account: PointsAccountEntity)

    @Query("DELETE FROM points_accounts WHERE id = :id")
    suspend fun deletePointsAccountById(id: Long)

    @Query("DELETE FROM points_accounts WHERE isDemo = 1")
    suspend fun deleteDemoPointsAccounts()
}
