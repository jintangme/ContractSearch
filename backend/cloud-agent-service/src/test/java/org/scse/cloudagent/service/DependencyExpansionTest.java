package org.scse.cloudagent.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.scse.cloudagent.config.AgentProperties;
import org.scse.cloudagent.domain.AgentRunState;
import org.scse.cloudagent.state.AgentStateMachine;
import org.scse.cloudagent.tool.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DependencyExpansionTest {
    @Test void lexicalHintsIgnoreCommentsAndStringContents() {
        var imports=SourceDependencies.imports("// import 'Bad.sol';\n string x=\"import 'Fake.sol';\"; /* import 'No.sol'; */ import {Policy as P} from './Policy.sol'; import './Roles.sol';");
        assertThat(imports).extracting(SourceDependencies.ImportRef::symbol).containsExactly("Policy","Roles");
    }
    @Test void nestedImportsAreReadBeforeFirstDecisionAndChargedToSharedBudget() {
        var catalog=mock(ContractCatalogClient.class);
        when(catalog.getContract(1,7)).thenReturn(Map.of("id",1,"contractName","Root","sourceCode","import './Policy.sol'; contract Root {}"));
        when(catalog.getContract(2,7)).thenReturn(Map.of("id",2,"contractName","Policy","sourceCode","import './Roles.sol'; contract Policy {}"));
        when(catalog.getContract(3,7)).thenReturn(Map.of("id",3,"contractName","Roles","sourceCode","contract Roles {}"));
        when(catalog.search("Policy","code",10,7)).thenReturn(List.of(Map.of("id",2,"contractName","Policy")));
        when(catalog.search("Roles","code",10,7)).thenReturn(List.of(Map.of("id",3,"contractName","Roles")));
        StructuredActionProvider provider=request->{
            assertThat(request.remainingToolCalls()).isEqualTo(3);
            assertThat(request.remainingRetrievalRounds()).isZero();
            assertThat(request.observations()).anyMatch(x->x.contains("contract Roles"));
            return AgentDecision.finalAnswer("Template sources read; deployed identity remains unverified",request.evidence().stream().map(e->e.id()).toList(),"done");
        };
        var result=engine(catalog,provider).execute(UUID.randomUUID(),7,"分析模板依赖关系",Map.of("selectedContractId",1));
        assertThat(result.state()).isEqualTo(AgentRunState.COMPLETE);
        assertThat(result.steps()).isEqualTo(1);assertThat(result.toolCalls()).isEqualTo(5);assertThat(result.retrievalRounds()).isEqualTo(2);
        assertThat(result.events().stream().filter(e->e.type().equals("dependency.resolved")).count()).isEqualTo(2);
    }
    @Test void ambiguousCatalogNamesAreNotArbitrarilyRead() {
        var catalog=mock(ContractCatalogClient.class);
        when(catalog.getContract(1,7)).thenReturn(Map.of("id",1,"contractName","Root","sourceCode","import './Policy.sol'; contract Root {}"));
        when(catalog.search("Policy","code",10,7)).thenReturn(List.of(Map.of("id",2,"contractName","Policy"),Map.of("id",3,"contractName","Policy")));
        StructuredActionProvider provider=request->{
            assertThat(request.observations()).anyMatch(x->x.contains("AMBIGUOUS"));
            return AgentDecision.finalAnswer("Version ambiguous",request.evidence().stream().map(e->e.id()).toList(),"done");
        };
        var result=engine(catalog,provider).execute(UUID.randomUUID(),7,"分析依赖",Map.of("selectedContractId",1));
        assertThat(result.toolCalls()).isEqualTo(2);
        verify(catalog,never()).getContract(2,7);verify(catalog,never()).getContract(3,7);
    }
    private MiniAgentLoopEngine engine(ContractCatalogClient catalog,StructuredActionProvider provider) {
        var mapper=new ObjectMapper();
        var props=new AgentProperties(true,"test","model",null,null,new AgentProperties.MiniLoop(true,6,8,2),null);
        return new MiniAgentLoopEngine(new AgentStateMachine(),new ToolRegistry(List.of(new SearchContractsTool(catalog),new GetContractTool(catalog))),new ToolInvocationValidator(mapper),new MiniLoopToolPolicy(),provider,new UntrustedContentBoundary(),mapper,props);
    }
}
