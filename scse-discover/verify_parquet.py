import pandas as pd
import sys

def verify_parquet(file_path, expected_count=3000):
    try:
        df = pd.read_parquet(file_path)
        actual_count = len(df)

        print(f"File: {file_path}")
        print(f"Expected: {expected_count} rows")
        print(f"Actual: {actual_count} rows")
        print(f"Status: {'✓ PASS' if actual_count == expected_count else '✗ FAIL'}")

        if actual_count > 0:
            print(f"\nColumns: {list(df.columns)}")
            # print(f"\nFirst row sample:")
            # print(df.iloc[0].to_dict())

        return actual_count == expected_count
    except Exception as e:
        print(f"Error: {e}")
        return False

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python verify_parquet.py <parquet_file_path> [expected_count]")
        sys.exit(1)

    file_path = sys.argv[1]
    expected = int(sys.argv[2]) if len(sys.argv) > 2 else 3000

    success = verify_parquet(file_path, expected)
    sys.exit(0 if success else 1)
