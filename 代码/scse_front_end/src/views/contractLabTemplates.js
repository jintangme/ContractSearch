export const proofStarterTemplate = `-- Proof Workbench Starter
-- Model contract state, then state a property about the model.
structure ContractState where
  dummy : Nat

def transition (s : ContractState) : ContractState :=
  s

theorem example_property (s : ContractState) :
  transition s = s := by
  rfl`

export const starterTemplates = [
  {
    id: 'blank-contract',
    name: 'BlankContract.sol',
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract NewContract {
    // TODO: define state variables

    constructor() {
        // TODO: initialize contract
    }

    // TODO: add functions
}`
  },
  {
    id: 'ownable-skeleton',
    name: 'OwnableSkeleton.sol',
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract OwnableSkeleton {
    address public owner;

    modifier onlyOwner() {
        require(msg.sender == owner, "Not owner");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    // TODO: add owner-only functions
}`
  },
  {
    id: 'token-skeleton',
    name: 'TokenSkeleton.sol',
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract TokenSkeleton {
    mapping(address => uint256) public balanceOf;

    // TODO: define token metadata
    // TODO: implement transfer logic
}`
  },
  {
    id: 'interface-skeleton',
    name: 'InterfaceSkeleton.sol',
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

interface IExample {
    // TODO: define external functions
}`
  },
  {
    id: 'library-skeleton',
    name: 'LibrarySkeleton.sol',
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

library ExampleLibrary {
    // TODO: add pure/helper functions
}`
  }
]

export const contractExamples = [
  {
    id: 'simple-storage-plus',
    name: 'SimpleStoragePlus.sol',
    title: 'SimpleStoragePlus',
    difficulty: 'Basic',
    proofTemplate: `-- Abstract model for SimpleStoragePlus.
structure StorageState where
  value : Nat
  owner : Nat

def setValue (s : StorageState) (x : Nat) : StorageState :=
  { s with value := x }

theorem setValue_correct (s : StorageState) (x : Nat) :
  (setValue s x).value = x := by
  rfl`,
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract SimpleStoragePlus {
    uint256 private value;
    address public owner;

    event ValueChanged(uint256 oldValue, uint256 newValue, address indexed operator);

    modifier onlyOwner() {
        require(msg.sender == owner, "Not owner");
        _;
    }

    constructor(uint256 initialValue) {
        owner = msg.sender;
        value = initialValue;
        emit ValueChanged(0, initialValue, msg.sender);
    }

    function setValue(uint256 newValue) public onlyOwner {
        uint256 oldValue = value;
        value = newValue;
        emit ValueChanged(oldValue, newValue, msg.sender);
    }

    function resetValue() public onlyOwner {
        uint256 oldValue = value;
        value = 0;
        emit ValueChanged(oldValue, 0, msg.sender);
    }

    function getValue() public view returns (uint256) {
        return value;
    }
}`
  },
  {
    id: 'access-controlled-vault',
    name: 'AccessControlledVault.sol',
    title: 'AccessControlledVault',
    difficulty: 'Intermediate',
    proofTemplate: `-- Abstract model for AccessControlledVault pause behavior.
structure VaultState where
  paused : Bool

def pause (s : VaultState) : VaultState :=
  { s with paused := true }

theorem pause_sets_paused (s : VaultState) :
  (pause s).paused = true := by
  rfl`,
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract AccessControlledVault {
    address public owner;
    bool public paused;
    mapping(address => uint256) public balances;

    event Deposited(address indexed account, uint256 amount);
    event Withdrawn(address indexed account, uint256 amount);
    event Paused(bool paused);

    modifier onlyOwner() {
        require(msg.sender == owner, "Not owner");
        _;
    }

    modifier whenNotPaused() {
        require(!paused, "Paused");
        _;
    }

    constructor() {
        owner = msg.sender;
    }

    function deposit() public payable whenNotPaused {
        require(msg.value > 0, "No value");
        balances[msg.sender] += msg.value;
        emit Deposited(msg.sender, msg.value);
    }

    function withdraw(uint256 amount) public whenNotPaused {
        require(balances[msg.sender] >= amount, "Insufficient balance");
        balances[msg.sender] -= amount;
        payable(msg.sender).transfer(amount);
        emit Withdrawn(msg.sender, amount);
    }

    function pause() public onlyOwner {
        paused = true;
        emit Paused(true);
    }

    function unpause() public onlyOwner {
        paused = false;
        emit Paused(false);
    }
}`
  },
  {
    id: 'mini-erc20',
    name: 'MiniERC20.sol',
    title: 'MiniERC20',
    difficulty: 'Intermediate',
    proofTemplate: `-- Abstract model for MiniERC20 transfer.
structure TokenState where
  senderBalance : Nat
  receiverBalance : Nat

def transfer (s : TokenState) (amount : Nat) : TokenState :=
  { senderBalance := s.senderBalance - amount,
    receiverBalance := s.receiverBalance + amount }

theorem receiver_gets_amount (s : TokenState) (amount : Nat) :
  (transfer s amount).receiverBalance = s.receiverBalance + amount := by
  rfl`,
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract MiniERC20 {
    string public name = "Mini Token";
    string public symbol = "MINI";
    uint8 public decimals = 18;
    uint256 public totalSupply;
    mapping(address => uint256) public balanceOf;

    event Transfer(address indexed from, address indexed to, uint256 value);

    constructor(uint256 initialSupply) {
        totalSupply = initialSupply;
        balanceOf[msg.sender] = initialSupply;
        emit Transfer(address(0), msg.sender, initialSupply);
    }

    function transfer(address to, uint256 value) public returns (bool) {
        require(to != address(0), "Zero address");
        require(balanceOf[msg.sender] >= value, "Insufficient balance");
        balanceOf[msg.sender] -= value;
        balanceOf[to] += value;
        emit Transfer(msg.sender, to, value);
        return true;
    }

    function circulatingSupply() public view returns (uint256) {
        return totalSupply - balanceOf[address(0)];
    }
}`
  },
  {
    id: 'escrow-payment',
    name: 'EscrowPayment.sol',
    title: 'EscrowPayment',
    difficulty: 'Intermediate',
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract EscrowPayment {
    enum State { Created, Funded, Released, Refunded }

    address public buyer;
    address public seller;
    address public arbiter;
    uint256 public amount;
    State public state;

    event Deposited(address indexed buyer, uint256 amount);
    event Released(address indexed seller, uint256 amount);
    event Refunded(address indexed buyer, uint256 amount);

    modifier onlyBuyer() {
        require(msg.sender == buyer, "Not buyer");
        _;
    }

    modifier onlyArbiter() {
        require(msg.sender == arbiter, "Not arbiter");
        _;
    }

    constructor(address seller_, address arbiter_) {
        buyer = msg.sender;
        seller = seller_;
        arbiter = arbiter_;
        state = State.Created;
    }

    function deposit() public payable onlyBuyer {
        require(state == State.Created, "Invalid state");
        require(msg.value > 0, "No value");
        amount = msg.value;
        state = State.Funded;
        emit Deposited(msg.sender, msg.value);
    }

    function release() public onlyArbiter {
        require(state == State.Funded, "Not funded");
        state = State.Released;
        payable(seller).transfer(amount);
        emit Released(seller, amount);
    }

    function refund() public onlyArbiter {
        require(state == State.Funded, "Not funded");
        state = State.Refunded;
        payable(buyer).transfer(amount);
        emit Refunded(buyer, amount);
    }
}`
  },
  {
    id: 'voting-box',
    name: 'VotingBox.sol',
    title: 'VotingBox',
    difficulty: 'Basic',
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract VotingBox {
    string public proposal;
    mapping(address => bool) public hasVoted;
    uint256 public yesVotes;
    uint256 public noVotes;

    event Voted(address indexed voter, bool support);

    constructor(string memory proposal_) {
        proposal = proposal_;
    }

    function vote(bool support) public {
        require(!hasVoted[msg.sender], "Already voted");
        hasVoted[msg.sender] = true;
        if (support) {
            yesVotes += 1;
        } else {
            noVotes += 1;
        }
        emit Voted(msg.sender, support);
    }

    function getResult() public view returns (string memory result) {
        if (yesVotes > noVotes) return "PASSED";
        if (noVotes > yesVotes) return "REJECTED";
        return "TIED";
    }
}`
  },
  {
    id: 'time-lock-wallet',
    name: 'TimeLockWallet.sol',
    title: 'TimeLockWallet',
    difficulty: 'Basic',
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract TimeLockWallet {
    address public owner;
    uint256 public unlockTime;

    event Received(address indexed sender, uint256 amount);
    event Withdrawn(address indexed owner, uint256 amount);

    constructor(uint256 unlockTime_) payable {
        require(unlockTime_ > block.timestamp, "Unlock time must be future");
        owner = msg.sender;
        unlockTime = unlockTime_;
    }

    receive() external payable {
        emit Received(msg.sender, msg.value);
    }

    function withdraw() public {
        require(msg.sender == owner, "Not owner");
        require(block.timestamp >= unlockTime, "Locked");
        uint256 amount = address(this).balance;
        payable(owner).transfer(amount);
        emit Withdrawn(owner, amount);
    }

    function balance() public view returns (uint256) {
        return address(this).balance;
    }
}`
  },
  {
    id: 'crowdfunding-lite',
    name: 'CrowdfundingLite.sol',
    title: 'CrowdfundingLite',
    difficulty: 'Intermediate',
    sourceCode: `// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

contract CrowdfundingLite {
    address public creator;
    uint256 public goal;
    uint256 public deadline;
    uint256 public pledged;
    bool public claimed;
    mapping(address => uint256) public contributions;

    event Pledged(address indexed backer, uint256 amount);
    event Claimed(uint256 amount);
    event Refunded(address indexed backer, uint256 amount);

    constructor(uint256 goal_, uint256 durationSeconds) {
        require(goal_ > 0, "Invalid goal");
        require(durationSeconds > 0, "Invalid duration");
        creator = msg.sender;
        goal = goal_;
        deadline = block.timestamp + durationSeconds;
    }

    function pledge() public payable {
        require(block.timestamp < deadline, "Ended");
        require(msg.value > 0, "No value");
        contributions[msg.sender] += msg.value;
        pledged += msg.value;
        emit Pledged(msg.sender, msg.value);
    }

    function claim() public {
        require(msg.sender == creator, "Not creator");
        require(block.timestamp >= deadline, "Not ended");
        require(pledged >= goal, "Goal not met");
        require(!claimed, "Already claimed");
        claimed = true;
        payable(creator).transfer(address(this).balance);
        emit Claimed(pledged);
    }

    function refund() public {
        require(block.timestamp >= deadline, "Not ended");
        require(pledged < goal, "Goal met");
        uint256 amount = contributions[msg.sender];
        require(amount > 0, "Nothing to refund");
        contributions[msg.sender] = 0;
        payable(msg.sender).transfer(amount);
        emit Refunded(msg.sender, amount);
    }
}`
  }
]
