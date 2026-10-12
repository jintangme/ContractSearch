package org.scse.cloudagent.service;

import java.util.Locale;

/** Conservative explicit artifact request detection; not a semantic intent model. */
final class TaskIntent {
    private TaskIntent() { }
    static boolean requiresModifiedSource(String task) {
        if (task == null) return false;
        StringBuilder scoped = new StringBuilder();
        for (String sentence : task.toLowerCase(Locale.ROOT).split("[。\\n；;!?！？]")) {
            // Conditional formatting boilerplate is not a request to edit code.
            if (sentence.matches("(?s).*(修改题|修复题|代码修复任务|for (code modification|repair tasks)).*")) continue;
            scoped.append(sentence).append(' ');
        }
        String text = scoped.toString()
                .replaceAll("(不要|无需|不需要|不必|勿|不要求)(修改|修复|新增|增加|改写|扩展|添加|实现)", "")
                .replaceAll("(do not|don't|without|no need to)\\s+(modify|repair|fix|add|rewrite|extend|implement)", "");
        boolean modification = text.matches("(?s).*(修改|修复|新增|增加|改写|扩展|添加|实现|\\brepair\\b|\\bfix\\b|\\bmodify\\b|\\badd\\b|\\brewrite\\b|\\bextend\\b|\\bimplement\\b).*" );
        boolean deliver = text.matches("(?s).*(交付|返回|给出|提供|输出).{0,60}(完整|修改后|修复后).{0,30}(源码|代码).*" )
                || text.matches("(?s).*(return|provide|deliver|write).{0,60}(full|complete|updated|fixed|modified).{0,30}(source|contract|code).*" );
        return modification && deliver;
    }
}
