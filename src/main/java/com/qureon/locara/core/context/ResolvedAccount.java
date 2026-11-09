package com.qureon.locara.core.context;

import com.qureon.locara.core.model.UIElementNode;

import java.util.Objects;

/**
 * Domain entity representing an active bank account card resolved dynamically from the mobile UI.
 */
public class ResolvedAccount {
    private final String accountNumber;
    private final String accountType;
    private final String holderName;
    private final boolean maskedBalancePresent;
    private final UIElementNode accountNode;

    public ResolvedAccount(String accountNumber,
                           String accountType,
                           String holderName,
                           boolean maskedBalancePresent,
                           UIElementNode accountNode) {
        this.accountNumber = accountNumber != null ? accountNumber.replaceAll("[^0-9]", "") : "";
        this.accountType = accountType != null ? accountType.trim() : "";
        this.holderName = holderName != null ? holderName.trim() : "";
        this.maskedBalancePresent = maskedBalancePresent;
        this.accountNode = accountNode;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getHolderName() {
        return holderName;
    }

    public boolean isMaskedBalancePresent() {
        return maskedBalancePresent;
    }

    public UIElementNode getAccountNode() {
        return accountNode;
    }

    public boolean isValid() {
        return accountNumber != null && accountNumber.length() >= 10 && accountNumber.length() <= 16;
    }

    @Override
    public String toString() {
        return "ResolvedAccount{" +
                "accountNumber='" + accountNumber + '\'' +
                ", accountType='" + accountType + '\'' +
                ", holderName='" + holderName + '\'' +
                ", maskedBalancePresent=" + maskedBalancePresent +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResolvedAccount)) return false;
        ResolvedAccount that = (ResolvedAccount) o;
        return maskedBalancePresent == that.maskedBalancePresent &&
                Objects.equals(accountNumber, that.accountNumber) &&
                Objects.equals(accountType, that.accountType) &&
                Objects.equals(holderName, that.holderName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber, accountType, holderName, maskedBalancePresent);
    }
}
