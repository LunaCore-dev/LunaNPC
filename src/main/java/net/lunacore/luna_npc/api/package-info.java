// The stable, public developer API for LunaNPC. Everything a consumer needs lives in this one
// package — import only {@code net.lunacore.luna_npc.api.*}.
//
// Entry point: {@link net.lunacore.luna_npc.api.LunaNpcApi}. Core types: {@code NpcDefinition} (a
// reusable template) and {@code NpcInstance} (a live NPC in the world); {@code NpcRegistry} /
// {@code AllianceRegistry} / {@code ZoneRegistry} / {@code RouteRegistry}; the immutable settings records
// ({@code NpcModelSettings}, {@code NpcCombatSettings}, {@code NpcSpawnSettings} (with its fly-only
// {@code NpcAirSpawn} height windows and its swim-only {@code NpcWaterSpawn} depth band),
// {@code NpcJobSettings} with its trade/quest/farm/patrol sub-type records, its bodyguard settings and its dialog tree
// ({@code NpcDialog}, {@code NpcDialogNode}, {@code NpcDialogChoice}, {@code NpcDialogCondition},
// {@code NpcDialogAction}), {@code NpcEquipmentSettings}, {@code NpcDropSettings}, ...); the domain
// types ({@code NpcAlliance}, {@code NpcZone}, {@code SurfaceMode}, {@code ZoneShape}, {@code NpcRoute}); the
// {@link net.lunacore.luna_npc.api.LunaNpcEvents} hooks (including {@code onDialogAction}); and the
// {@code ReactionMode} / {@code AttackIntentResult} / {@code NpcJobType} / {@code NpcStockScope} /
// {@code NpcObjectiveType} / {@code NpcQuestCompletion} / {@code NpcDialogActionType} /
// {@code NpcDialogConditionType} / {@code NpcRegenConditionType} / {@code NpcRangeMetric} /
// {@code NpcRangeComparator} / {@code NpcAttackRange} enums. Combat also carries the close/long range
// switch ({@code NpcRangeSwitch} of priority {@code NpcRangeRule}s) inside {@code NpcCombatSettings}.
// {@code NpcCombatMode} chooses SIMPLE vs the node-based ADVANCED combat ({@code NpcAdvancedCombat}: an
// attack library of {@code NpcCombatAttack}s and a flat list of {@code NpcCombatRule}s, each a
// {@code NpcCombatTrigger} + {@code NpcCombatCondition}s (value-vs-value over {@code NpcValueSource}) +
// {@code NpcCombatAction}s targeting via {@code NpcTargetSelector} and placed via {@code NpcCombatPlacement}).
//
// In-game stick access control lives in {@link net.lunacore.luna_npc.api.StickPermissions} (via
// {@code LunaNpcApi.permissions(server)}): per-{@code LunaStick} grants to players or alliances, plus the
// {@code PermissionLogEntry}/{@code PermissionLogAction} audit log. {@code LunaNpcEvents.onPermissionChanged}
// fires when a grant set changes.
//
// Everything the API hands you is an interface or an immutable value — no internal engine state can be
// reached or mutated through it. Everything OUTSIDE this package (net.lunacore.luna_npc.npc / .entity /
// .zone / .alliance / .network / ...) is internal implementation and may change without notice.
//
// Call the API on the server thread (see {@link net.lunacore.luna_npc.api.LunaNpcApi#runOnServerThread}),
// and check {@link net.lunacore.luna_npc.api.LunaNpcApi#apiVersion()} before relying on newer methods.
package net.lunacore.luna_npc.api;
