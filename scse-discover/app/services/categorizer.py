def categorize_contract(contract_name, source_code, abi):
    name_lower = contract_name.lower()
    source_lower = source_code.lower()
    abi_lower = abi.lower()

    if any(keyword in name_lower or keyword in source_lower for keyword in ["erc721", "erc1155", "nft"]):
        return "NFT"
    elif any(keyword in name_lower or keyword in source_lower for keyword in ["erc20", "token"]):
        return "Token"
    elif any(keyword in name_lower or keyword in source_lower for keyword in ["swap", "pool", "liquidity", "defi", "lending", "vault"]):
        return "DeFi"
    else:
        return "Other"
