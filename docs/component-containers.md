# Card and upgrade containers (T09 / T10)

Behavior reference: MightyPirates/OpenComputers at
`258943363ea8fc4ef14948e6bc6f67375061580c`:

| Family | Original item / driver | Java implementation |
| --- | --- | --- |
| Card containers | `common/item/UpgradeContainerCard.scala`, `integration/opencomputers/DriverContainerCard.scala` | `common/item/AgentUpgradeItem.java`, `server/driver/item/AgentUpgrade.java` |
| Upgrade containers | `common/item/UpgradeContainerUpgrade.scala`, `integration/opencomputers/DriverContainerUpgrade.scala` | `common/item/AgentUpgradeItem.java`, `server/driver/item/AgentUpgrade.java` |

Original paths are under `src/main/scala/li/cil/oc/`; Java paths are under
`src/main/java/li/cil/oc/`. Original licensing and attribution are retained by
the repository's license and notices.

Both families are structural slot descriptors. Their own slot is `Container`;
the provided slot is respectively `Card` or `Upgrade`. The three tiers use
internal values 0, 1 and 2, with displayed maximum tiers 1, 2 and 3. They create
no managed environment and store no separate inventory inside the container
item. Their original drivers impose no host restriction. `AgentUpgrade` thus
accepts these two kinds in non-Agent driver queries as well as Agent hosts.
The separate inventory-capacity upgrade is handled by its own driver.

The item tooltip uses the original `oc:tooltip.upgradecontainercard` or
`oc:tooltip.upgradecontainerupgrade` key, splits the existing `[nl]` marker,
and passes the displayed tier (`tier + 1`). Existing translations provide
the text.

`RobotController` and `AgentMenu` enforce the descriptor's provided slot and
maximum tier, with one component per equipment slot. Equipment removal calls
`AgentRuntime.removeComponent` before replacing the stack. The runtime keeps
the original component stack by identity, detaches the environment and saves
its registry-aware data through the item's driver into that removed stack.
Rebuilding equipment preserves unchanged environments. The robot persists its
equipment separately from its container descriptors, so installed card and
upgrade data belongs to the installed item, including after removal and
reinsertion.

The two container families and their equipment lifecycle match the pinned
source. The complete Windows build and the existing device regression passed.
