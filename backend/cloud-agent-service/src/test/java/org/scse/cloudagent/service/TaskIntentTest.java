package org.scse.cloudagent.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class TaskIntentTest {
    @Test void conditionalFormattingDoesNotTurnAnalysisIntoRepair() {
        assertThat(TaskIntent.requiresModifiedSource("解释退款状态变化。修改题交付一个完整solidity代码块")).isFalse();
        assertThat(TaskIntent.requiresModifiedSource("Explain the source. For code modification deliver complete source.")).isFalse();
        assertThat(TaskIntent.requiresModifiedSource("无需修改，给出完整源码并解释")).isFalse();
    }
    @Test void explicitChangesAreRecognizedWithoutDictatingAWorkflow() {
        assertThat(TaskIntent.requiresModifiedSource("增加权限控制。交付完整源码。")).isTrue();
        assertThat(TaskIntent.requiresModifiedSource("不要修改旧接口，增加一个方法，交付完整源码")).isTrue();
        assertThat(TaskIntent.requiresModifiedSource("Extend the counter and provide the complete source")).isTrue();
    }
}
