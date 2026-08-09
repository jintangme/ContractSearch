from pydantic import BaseModel

class ContractAddressResponse(BaseModel):
    file_path: str
    count: int

class ContractInfoRequest(BaseModel):
    file_path: str

class ContractInfoResponse(BaseModel):
    file_path: str
    count: int
