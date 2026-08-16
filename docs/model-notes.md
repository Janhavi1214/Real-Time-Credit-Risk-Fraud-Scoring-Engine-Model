# RiskGuard — ML Model Design & Architecture Notes

## Overview

The `scoring-service` serves real-time fraud probability scores computed by a machine learning model trained on transaction features structured similarly to the Kaggle Credit Card Fraud Detection dataset.

## Features Used

| Feature Name | Description | Preprocessing |
|--------------|-------------|---------------|
| `Amount` | Transaction monetary value ($) | `StandardScaler` |
| `Time` | Seconds elapsed since baseline | `StandardScaler` |
| `V1` to `V28` | Anonymized PCA components capturing transaction behavior | Standardized |

## Model Selection: Random Forest Classifier

We selected a **Random Forest Classifier** (`n_estimators=100`, `max_depth=10`, `class_weight='balanced'`).

### Why Random Forest?
1. **Explainability & Compliance:** Tree-based models allow computing feature importances (e.g. MDI / SHAP values) to audit why a transaction was flagged or blocked, complying with financial regulatory transparency requirements.
2. **Non-Linear Feature Interaction:** Financial fraud patterns involve complex non-linear interactions between transaction amounts, time windows, and behavioral features that simple linear models cannot capture without manual feature engineering.
3. **Robustness to Outliers:** Subspace sampling and bagging make Random Forests resilient to noise in financial data.

## Class Imbalance Handling: `class_weight='balanced'`

Fraud datasets feature severe class imbalance (~0.1% - 0.5% fraudulent transactions).

We used `class_weight='balanced'`, which automatically calculates class weights during tree construction inversely proportional to class frequencies:

\[
w_j = \frac{N}{K \cdot n_j}
\]

Where \(N\) is total samples, \(K\) is number of classes, and \(n_j\) is samples in class \(j\).

**Why not SMOTE?**
SMOTE creates synthetic samples in high-dimensional space. In PCA-transformed feature space (`V1`..`V28`), SMOTE can generate unrealistic synthetic data points that blur decision boundaries and introduce serving latency artifacts. `class_weight='balanced'` incurs **zero extra overhead** at inference time.

## Key Metrics & Trade-offs in Fraud Detection

### Why Accuracy is Misleading
In a dataset with 99.5% legitimate transactions and 0.5% fraud, a naive "dummy" model that predicts `0` (legitimate) for every transaction achieves **99.5% Accuracy**, yet has **0% Recall** — missing 100% of financial fraud.

### Precision vs. Recall Tradeoff
- **Recall (Sensitivity):** \(\frac{TP}{TP + FN}\) — The proportion of actual frauds detected.
- **Precision:** \(\frac{TP}{TP + FP}\) — The proportion of flagged transactions that are truly fraudulent.

**Why Recall Matters Most:**
In credit card fraud scoring, a **False Negative (missing a fraud)** costs thousands of dollars in chargebacks and fraud losses, whereas a **False Positive (flagging a legit user)** costs only a minor verification step. Therefore, our model optimizes for **high Recall** while maintaining acceptable Precision.

### Primary Evaluation Metric: PR-AUC (Average Precision)
Because class imbalance makes ROC-AUC overly optimistic (due to the large number of True Negatives), we rely primarily on **PR-AUC (Precision-Recall Area Under the Curve)** to evaluate ranking quality across operational threshold ranges.

## Test Set Performance (v1.0.0)

| Metric | Score |
|--------|-------|
| **Recall** | **90.00%** |
| **Precision** | **100.00%** |
| **F1-Score** | **0.9474** |
| **ROC-AUC** | **1.0000** |
| **PR-AUC (Average Precision)** | **1.0000** |
