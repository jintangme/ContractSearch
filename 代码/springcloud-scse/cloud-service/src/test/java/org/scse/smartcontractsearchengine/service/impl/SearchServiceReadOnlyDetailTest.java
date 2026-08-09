package org.scse.smartcontractsearchengine.service.impl;

import org.junit.jupiter.api.Test;
import org.scse.smartcontractsearchengine.mapper.SmartContractMapper;
import org.scse.smartcontractsearchengine.mapper.UserFavoriteMapper;
import org.scse.smartcontractsearchengine.model.entity.SmartContract;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchServiceReadOnlyDetailTest {
    @Test
    void readOnlyDetailDoesNotCompileOrPersistMissingAbi() {
        SmartContractMapper mapper = mock(SmartContractMapper.class);
        UserFavoriteMapper favorites = mock(UserFavoriteMapper.class);
        SolidityAbiCompilerService compiler = mock(SolidityAbiCompilerService.class);
        SmartContract contract = new SmartContract();
        contract.setId(42L);
        contract.setContractName("ReadOnlyCandidate");
        contract.setSourceCode("pragma solidity ^0.8.20; contract ReadOnlyCandidate {}");
        contract.setAbiStatus("failed");
        when(mapper.selectById(42L)).thenReturn(contract);

        SearchServiceImpl service = new SearchServiceImpl();
        ReflectionTestUtils.setField(service, "smartContractMapper", mapper);
        ReflectionTestUtils.setField(service, "userFavoriteMapper", favorites);
        ReflectionTestUtils.setField(service, "solidityAbiCompilerService", compiler);

        var detail = service.getContractById(42L, false);

        assertThat(detail.getId()).isEqualTo(42L);
        assertThat(detail.getSourceCode()).contains("ReadOnlyCandidate");
        verifyNoInteractions(compiler);
        verify(mapper, never()).updateById(contract);
    }
}
