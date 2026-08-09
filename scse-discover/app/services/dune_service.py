from dune_client.client import DuneClient
import logging

logger = logging.getLogger(__name__)

def fetch_contracts_by_query(api_key: str, query_id: int) -> list[str]:
    """根据指定的 api_key 和 query_id 查询合约地址列表"""
    if not api_key:
        raise ValueError("api_key is required")
    if not query_id:
        raise ValueError("query_id is required")

    logger.info(f"Fetching Dune query: {query_id}")
    dune = DuneClient(api_key)
    query_result = dune.get_latest_result(query_id)

    rows = query_result.result.rows
    logger.info(f"Query {query_id} returned {len(rows)} rows")

    if not rows:
        logger.warning(f"Query {query_id} returned no rows")
        return []

    # 打印实际列名和前5行
    logger.info(f"Query {query_id} columns: {list(rows[0].keys())}")
    for i, row in enumerate(rows[:5]):
        logger.info(f"Query {query_id} row[{i}]: {row}")

    addresses = [row.get("address") for row in rows]
    none_count = addresses.count(None)
    if none_count > 0:
        logger.warning(f"Query {query_id}: {none_count} rows missing 'address' column, available keys: {list(rows[0].keys())}")
        addresses = [a for a in addresses if a is not None]

    logger.info(f"Query {query_id} valid addresses: {len(addresses)}")
    return addresses
