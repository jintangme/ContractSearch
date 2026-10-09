-- Original, minimal examples authored for this repository.
-- No license grant is made here; licensing follows the project decision.
-- Synthetic demo identifiers are not on-chain addresses. No user rows are created.
-- No fake embeddings: use code/BM25 search immediately; semantic search requires
-- real vectors generated with the same embedding model used for query encoding.
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO smart_contract (address, contract_name, source_code, abi, abi_status, compiler_version, category, description, is_effective)
VALUES
('demo:storage', 'DemoStorage', $sol$
// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.20;

/// @notice Educational storage example, never deployed to a public chain.
contract DemoStorage {
    uint256 public value;
    event ValueChanged(uint256 newValue);

    function setValue(uint256 newValue) external {
        value = newValue;
        emit ValueChanged(newValue);
    }
}
$sol$, '[{"type":"function","name":"value","inputs":[],"outputs":[{"name":"","type":"uint256"}],"stateMutability":"view"},{"type":"function","name":"setValue","inputs":[{"name":"newValue","type":"uint256"}],"outputs":[],"stateMutability":"nonpayable"},{"type":"event","name":"ValueChanged","inputs":[{"name":"newValue","type":"uint256","indexed":false}],"anonymous":false}]', 'generated', '0.8.35', 'Tools', 'Original demo: public numeric storage with a setter and ValueChanged event. 教学示例：保存数值、读取状态和观察事件。', true),
('demo:owner-counter', 'DemoOwnerCounter', $sol$
// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.20;

/// @notice Educational owner permission example; not a token contract.
contract DemoOwnerCounter {
    address public immutable owner;
    uint256 public count;

    constructor() { owner = msg.sender; }

    function increment() external {
        require(msg.sender == owner, "owner only");
        count += 1;
    }
}
$sol$, '[{"type":"constructor","inputs":[],"stateMutability":"nonpayable"},{"type":"function","name":"owner","inputs":[],"outputs":[{"name":"","type":"address"}],"stateMutability":"view"},{"type":"function","name":"count","inputs":[],"outputs":[{"name":"","type":"uint256"}],"stateMutability":"view"},{"type":"function","name":"increment","inputs":[],"outputs":[],"stateMutability":"nonpayable"}]', 'generated', '0.8.35', 'Tools', 'Original demo: owner-only counter increment with immutable deployer authority. 教学示例：管理员权限与计数器。', true),
('demo:pause-switch', 'DemoPauseSwitch', $sol$
// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.20;

/// @notice Educational pause switch with a guarded operation.
contract DemoPauseSwitch {
    address public immutable owner;
    bool public paused;
    uint256 public operations;

    constructor() { owner = msg.sender; }

    function setPaused(bool next) external {
        require(msg.sender == owner, "owner only");
        paused = next;
    }

    function operate() external {
        require(!paused, "paused");
        operations += 1;
    }
}
$sol$, '[{"type":"constructor","inputs":[],"stateMutability":"nonpayable"},{"type":"function","name":"owner","inputs":[],"outputs":[{"name":"","type":"address"}],"stateMutability":"view"},{"type":"function","name":"paused","inputs":[],"outputs":[{"name":"","type":"bool"}],"stateMutability":"view"},{"type":"function","name":"operations","inputs":[],"outputs":[{"name":"","type":"uint256"}],"stateMutability":"view"},{"type":"function","name":"setPaused","inputs":[{"name":"next","type":"bool"}],"outputs":[],"stateMutability":"nonpayable"},{"type":"function","name":"operate","inputs":[],"outputs":[],"stateMutability":"nonpayable"}]', 'generated', '0.8.35', 'Tools', 'Original demo: owner-controlled pause flag guarding a public operation. 教学示例：暂停开关、访问控制和运行状态。', true)
ON CONFLICT (address) DO UPDATE SET
    source_code = EXCLUDED.source_code,
    abi = EXCLUDED.abi,
    abi_status = EXCLUDED.abi_status,
    compiler_version = EXCLUDED.compiler_version,
    description = EXCLUDED.description
WHERE smart_contract.uploader_id IS NULL
  AND smart_contract.address IN ('demo:storage', 'demo:owner-counter', 'demo:pause-switch');

INSERT INTO group_table (group_title, group_description)
SELECT 'Original demo contracts', 'Three small repository-authored examples for browse, search and Contract Lab. Not deployed contracts or an audit dataset.'
WHERE NOT EXISTS (SELECT 1 FROM group_table WHERE group_title = 'Original demo contracts');
INSERT INTO group_contract (group_id, contract_id, order_num)
SELECT g.id, c.id, CASE c.address WHEN 'demo:storage' THEN 1 WHEN 'demo:owner-counter' THEN 2 ELSE 3 END
FROM group_table g CROSS JOIN smart_contract c
WHERE g.group_title = 'Original demo contracts'
  AND c.address IN ('demo:storage', 'demo:owner-counter', 'demo:pause-switch')
ON CONFLICT (group_id, contract_id) DO NOTHING;
COMMIT;
