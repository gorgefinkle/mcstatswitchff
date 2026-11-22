# Freaky Friday — player command identity routing (experimental milestone)

## Target behavior

When account **Fred** controls the **Sally physical player body**, the user
command source's entity and `@s` must resolve Sally, while Fred's login
authentication, permission set and feedback connection remain Fred's.
`/give fred apple 1` and `/give sally apple 1` continue targeting
the physical players with those names in Minecraft's ordinary player
registry. No command-name rewrite should be necessary.

The implementation intercepts NeoForge's **CommandEvent**, which runs
for vanilla and third-party commands using normal Brigadier server-side
execution. On an already-authorized committed mind swap, it:
1. Requires an authenticated player-origin CommandSource (not console,
   command blocks, or `execute as` impersonation).
2. Resolves the session's occupied physical ServerPlayer body.
3. Projects that body as the command entity, position, rotation and level,
   without replacing the connected player's CommandSource, privileges or
   callback/feedback receiver.
4. Cancels the original invocation and re-dispatches the **unchanged**
   command with Minecraft's normal `performPrefixedCommand` pipeline,
   because setting replacement parse results alone did not affect the
   final execution source on tested NeoForge 26.2. The projected source
   retains the authentic sender; the nested command cannot be rerouted
   again by the authenticated connection guard.
5. Invalidates both sides on logout or server stop and refuses to
   execute commands under missing/stale body routes.
6. Verifies that the unfamiliar addon command is executed exactly once.

Caveat: NeoForge command-event listeners may observe both the original
canceled event and the re-dispatched event. This can matter for unusual
plugins that perform side effects inside their `CommandEvent` listener.
Those are not automatically safe or verified by ordinary Brigadier-command
tests; a pre-parse command-source hook would avoid double event delivery.

No individual vanilla or addon command is intercepted or rewritten.

## Unknown third-party command test

`dev.unseenaddon.UnseenAddonCommandFixture` is a deliberately unrelated
Brigadier command provider, activated ONLY under the disposable
`gameTestServer` property `freakyfriday.thirdPartyFixture=true`.
It has no knowledge of Freaky Friday's command route state and is
never registered on ordinary player servers.

The `third_party_command_route` GameTest creates two test players,
simulates a verified logical player command route and checks:
- default unswapped source and command behavior
- both directions of `getPlayerOrException()` after exchange
- unfamiliar addon `@s` entity selection **when** Minecraft resolves
  GameTest's non-online mock players (otherwise explicit UNTESTED)
- original authenticated source, permissions and command feedback
- lack of redirection for console/command-block sources using an entity
- simultaneous two-player cleanup and post-cancel restoration
- named player lookup if Minecraft's test harness exposes two distinct
  players through its live PlayerList (otherwise clearly logs UNTESTED).
The test always verifies direct `CommandSourceStack.getPlayerOrException()`
and **does not** silently substitute custom `@s` handling for Minecraft's
real player argument parser. True live two-player login/name resolution
still requires a separate connected-client integration test.

A new required CI success marker applies to both Minecraft 26.2 and 26.3.

## Hard limitations

**This is command-context routing, not yet a full player gameplay swap.**
The active book path does not activate a real player-control transfer,
and this command router does not move network packet handling,
the rendering camera, inventory UI, damage input, player position or
third-party account-specific data. It deliberately has NO player chat
command for manually activating routes; only a future committed
player-mind exchange engine can bind them.

Third-party commands that access `CommandSourceStack.getPlayerOrException()`,
`getEntity()`, player selectors, etc. can inherit this behavior. Commands
that instead read the authenticated connection UUID, `source.source`,
a platform proxy, offline user database, plugin custom account mapping,
or bypass NeoForge's Brigadier event cannot be universally redirected
by changing the source entity. Some integrations require explicit opt-in;
security checks must not accidentally grant one account another account's
operator permissions. Authentication remains account-owned even when
gameplay identity is projected.

Do not claim universal compatibility with all mod/plugin commands,
and do not claim player↔player swaps are implemented from this test.
