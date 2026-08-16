"""
Synthetic Dataset Generator for Credit Card Fraud Detection.

Note on synthetic fallback:
We generate a realistic synthetic dataset mimicking the Kaggle Credit Card Fraud Detection
dataset structure (Time, V1..V28 PCA features, Amount, Class). This guarantees reproducible,
offline execution without requiring external Kaggle API credentials or manual downloads.

Dataset properties:
- 10,000 transactions
- 28 anonymized PCA features (V1..V28) + Time + Amount
- Severe class imbalance: ~0.5% fraudulent transactions (Class=1)
"""

from pathlib import Path
import numpy as np
import pandas as pd


def generate_dataset(
    output_path: Path, n_samples: int = 10000, fraud_rate: float = 0.005, random_seed: int = 42
):
    np.random.seed(random_seed)

    n_fraud = int(n_samples * fraud_rate)
    n_legit = n_samples - n_fraud

    # Generate Time (seconds over 2 days)
    time_legit = np.sort(np.random.uniform(0, 172800, n_legit))
    time_fraud = np.sort(np.random.uniform(0, 172800, n_fraud))

    # Generate Amount ($1 to $2000 for legit, higher mean for fraud)
    amount_legit = np.random.exponential(scale=88.0, size=n_legit) + 1.0
    amount_fraud = np.random.exponential(scale=350.0, size=n_fraud) + 20.0

    # Generate V1..V28 (PCA components)
    # Legitimate transactions: mean ~ 0, std ~ 1
    v_legit = np.random.normal(loc=0.0, scale=1.0, size=(n_legit, 28))

    # Fraudulent transactions: shifted distributions on key features (V1, V3, V4, V10, V12, V14)
    v_fraud = np.random.normal(loc=0.0, scale=1.0, size=(n_fraud, 28))
    v_fraud[:, 0] -= 2.5  # V1 shift
    v_fraud[:, 2] -= 3.0  # V3 shift
    v_fraud[:, 3] += 2.0  # V4 shift
    v_fraud[:, 9] -= 3.5  # V10 shift
    v_fraud[:, 11] -= 4.0  # V12 shift
    v_fraud[:, 13] -= 4.5  # V14 shift

    # Combine into DataFrames
    columns_v = [f"V{i}" for i in range(1, 29)]

    df_legit = pd.DataFrame(v_legit, columns=columns_v)
    df_legit["Time"] = time_legit
    df_legit["Amount"] = amount_legit
    df_legit["Class"] = 0

    df_fraud = pd.DataFrame(v_fraud, columns=columns_v)
    df_fraud["Time"] = time_fraud
    df_fraud["Amount"] = amount_fraud
    df_fraud["Class"] = 1

    df = pd.concat([df_legit, df_fraud], ignore_index=True)
    # Shuffle dataset
    df = df.sample(frac=1.0, random_state=random_seed).reset_index(drop=True)

    # Save to CSV
    output_path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(output_path, index=False)
    print(
        f"Generated dataset at '{output_path}' with {len(df)} rows. Fraud count: {df['Class'].sum()} ({df['Class'].mean():.2%})"
    )


if __name__ == "__main__":
    data_dir = Path(__file__).parent
    generate_dataset(data_dir / "creditcard.csv")
