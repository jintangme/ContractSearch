# Contract Discovery API

REST API for fetching hot Ethereum contract addresses from Dune Analytics and retrieving detailed contract information from Etherscan.

## Setup

Recommended runtime: Python 3.11. The pinned pandas/pyarrow versions are not compatible with every newer Python release.

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Create `.env` file from template:
```bash
cp .env.example .env
```

3. Add your API keys to `.env`:
```
DUNE_API_KEY=your_dune_api_key
ETHERSCAN_API_KEY=your_etherscan_api_key
```

## Run

```bash
uvicorn app.main:app --reload
```

API will be available at `http://localhost:18000`

## Endpoints

### GET /get/contracts/address
Fetches hot contract addresses from Dune and saves to text file.

**Response:**
```json
{
  "file_path": "data/contracts_20260327_143022.txt",
  "count": 300
}
```

### POST /get/contracts/info
Retrieves detailed contract information from Etherscan.

**Request:**
```json
{
  "file_path": "data/contracts_20260327_143022.txt"
}
```

**Response:**
```json
{
  "file_path": "data/contracts_info_20260327_143022.parquet",
  "count": 300
}
```

## API Documentation

Interactive docs: `http://localhost:18000/docs`
