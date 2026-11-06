# Slayer from Hell (Android)

A first-person shooter test scene in the style of Quake/Doom, written from scratch in Java with OpenGL ES 3.0.

* **PBR rendering**: GGX/Smith/Schlick shading of albedo, normal, height, ambient-occlusion, roughness, metalness and emission maps, ACES tone mapping, fog.
* **Parallax occlusion mapping**: an adaptive ray march through each material's height map with binary-search refinement, plus soft self-shadows toward the nearest lights.
* **Procedural materials**: every texture is generated at start-up (hell brick, riveted metal panels, cracked floor tiles, diamond plate, concrete, crates, light panels, carved pillars, flowing lava, gunmetal, walnut, leather, fabric, brass, shell hulls).
* **Test level**: a start room with pillars and crates, a corridor, a 9 m hall with stairs up to a balcony and a lava pit, and a store room. Lights are masked per cell so they don't shine through walls.
* **Quake-style controller**: ground friction/acceleration, air control (strafe-jumping works), jumping, stepping up stairs, head bob, landing dip, recoil kick.
* **Double-barrel shotgun, baked from high poly**: `tools/bake/GunBaker.java` sculpts a 5.5-million-triangle side-by-side shotgun (hollow bores and chambers, serrated rib, brass bead, case-hardened action with scroll engraving, slotted screws, hinge pin, top lever, safety, triggers and guard, chequered walnut, ribbed butt pad) and bakes it onto a 744-triangle game mesh (UV-unwrapped into 79 charts on a 512² atlas, 1.7 mm per texel): normal, height, ambient occlusion, albedo, roughness and metalness maps (`app/src/main/assets`). Rebuild with `javac tools/bake/GunBaker.java && java -Xmx4g -cp tools/bake GunBaker app/src/main/assets /tmp/preview [mesh]`.
* **Third person, playing as the warden**: an over-the-right-shoulder camera that tightens when she aims and pulls in rather than clipping through walls. She is a rigid-segment rig over the baked low poly (`hero.bin` holds one mesh per body part, 2,274 triangles without the glaive) with procedural, velocity-driven animation (`Hero.java`): each foot plants during stance and arcs through the swing, with stride and cadence scaled by speed and a walk-to-run blend, heel-strike and toe-off roll, and two-bone IK for the legs; the pelvis bobs, shifts, rolls and twists; the torso counter-rotates, leans into the run and bends with the aim; jumps tuck the legs and landings compress the body; the braid swings on a spring. A light push on the stick walks, a full push runs. The double barrel replaces her glaive: stock in the shoulder, both hands placed by arm IK on the grip and forend (the left hand fetches shells during the break-open reload). She carries it at low ready and turns toward where she is going; when shooting or reloading she squares up to the camera. Pellets leave the muzzle toward whatever the crosshair is on.
* **Locomotion (in the spirit of GTA V)**: start / cycle / stop / pivot behaviour driven by her real velocity.
  * Feet plant without sliding, with stride matched to speed and heel strike and toe off.
  * Foot IK finds the floor under each foot, so on stairs each foot lands on its own step and the pelvis drops to let the lower leg reach.
  * She leans into starts, rocks back on stops and banks into turns. Turning on the spot makes her shuffle her feet round.
  * Walking into a wall makes her recoil; running into one at full speed knocks her down. Pushing against a wall, she puts her free hand on it.
  * Idle fidgets play at random (looking around, shifting weight, checking and revving the saw, rolling her shoulders), and every step varies a little.
  * With no shotgun, her free arm swings as she walks, pumps when she runs and comes up to guard when the saw is out.
* **Character simulation (active ragdoll with muscles)**: see "Character simulation architecture" below.
* **Chainsaw arm, baked from high poly**: `tools/bake/ChainsawBaker.java` sculpts a forearm-mounted chainsaw she slides her right arm into (padded opening, grip bar inside, riveted bands, leather straps and buckles; a two-stroke engine with fuel tank, finned cylinder, head cover, plug and lead, muffler and twin pipes, recoil starter and air filter; front housing with brass nuts, chain-brake guard on its hinge, bucking spikes; a 53 cm guide bar; and a shell caddy). The paint is amber hazard chevrons over bare-metal chips and grime. Every link of the 136-link chain is modelled. The low poly is box modelled at 902 triangles (818 body + 84 chain loop): the major forms are geometry and everything small goes to the bake (normal, height, AO, albedo, roughness, metalness on a 512² atlas at 2.6 mm per texel). The chain bakes onto a tiling 8-link strip (`chain_*.png`), so the chain loop scrolls its texture to run. Rebuild with `javac tools/bake/ChainsawBaker.java && java -Xmx4g -cp tools/bake ChainsawBaker app/src/main/assets /tmp/preview [low|high]`.
* **Shotgun in one hand, chainsaw on the other**: hold SAW to raise the chainsaw and rev it (the chain speeds up, the engine note climbs, exhaust puffs from the pipes). Where the bar touches a wall it throws sparks and leaves scars. FIRE shoots the double barrel one-handed from the hip, with the stock under her left arm. The reload is one-handed: she flicks the gun open, the empties fly, and she brings her saw arm across and pushes the open chambers down onto the two spare shells standing in the caddy on its sleeve, then flicks it shut.
* **The warden, baked from high poly**: `tools/bake/HeroBaker.java` sculpts a part-based figure (3.3 million triangles when meshed at 1.5 mm): charcoal undersuit, army-green painted steel plates with panel grooves, rivets, soot, scratches and edges chipped to bare steel, dark steel trim, a crested helmet with a glowing amber visor slit, a long braid, leather belt, pouches and gauntlets, a crimson sash, and a glaive whose obsidian blade glows through molten cracks. Each part's low poly is lofted through rings fitted to the sculpt by ray casting (2,516 triangles with the glaive, 49 UV charts, 512² atlas at 4.6 mm per texel) and baked to normal, height, AO, albedo, roughness, metalness and emission maps. Rebuild with `javac tools/bake/HeroBaker.java && java -Xmx6g -cp tools/bake HeroBaker app/src/main/assets /tmp/preview [low|prev|high|obj]`.
* **Break-action**: fire the right then the left barrel (heavy kick, 9 buckshot pellets each), break it open on the hinge, the extractors throw the fired cases over your shoulder, the left hand loads fresh shells and snaps it shut. Bullet holes, sparks, dust and splinters, muzzle flash light and smoke.
* **Sound**: all effects are synthesised in code.

Controls: left side = move stick, right side = drag to look (also while holding Fire), FIRE / RELOAD / JUMP buttons (hold Jump to bunny-hop).

The APK is built by GitHub Actions (Actions → latest run → artifact `SlayerFromHell-debug`).


## Character simulation architecture

The engine has no third-party physics, so the warden's body is simulated by code written for this game.

**Update order** (one owner per bone at a time):
1. `Player` moves her capsule and reports wall hits.
2. `Hero` generates the animated pose: gait, foot IK, leans, reactions and arms.
3. `ActiveRagdoll` turns that pose (or a protective or recovery pose) into muscle targets.
4. `PhysWorld` steps at a fixed 60 Hz.
5. The rendered pose comes from the animation, from the bodies, or from a blend of the two.

**Classes:**
* `PhysWorld`: substepped XPBD rigid-body solver (Müller et al. 2020).
  * Each substep integrates gravity, muscle torques and assist forces explicitly, then solves joint anchors, joint limits and contacts as positional constraints.
  * Velocities are derived from the change in pose, then a velocity pass applies restitution, dynamic friction and joint damping.
  * Contacts are spheres along each body's capsules against the level grid (floors, steps, walls, ceilings). Static friction is positional, so planted feet don't slide.
  * Non-finite states are detected, logged and reset.
* `RigidBody`: position, orientation (quaternion), velocities, mass, diagonal capsule inertia and capsule colliders. The body frame is the bind pose at the segment's centre of mass.
* `PhysJoint`: ball or hinge with anatomical limits (swing cone and twist, or hinge range), plus a muscle.
  * The muscle is a PD actuator with quaternion error: `τ = Kp·e + Kd·(ω_target − ω_rel) + τ_ff`.
  * The feedforward is gravity compensation of the limb below.
  * Torque saturates at the joint's maximum × strength × (1 − fatigue). Fatigue builds while the muscle is saturated.
  * Gains come from a natural frequency and damping ratio times the joint's effective inertia, clamped to what the substep can integrate stably.
* `ActiveRagdoll`: the character simulation manager. It builds 13 bodies from height and mass using anthropometric segment fractions; the chainsaw and the shotgun add their mass. Modes:
  * **animated**: the animation drives; the bodies follow it kinematically.
  * **reacting**: a powered ragdoll. Muscles track the animation and an assist keeps her upright. Balance is judged from the centre of mass, its velocity and the support polygon of the planted feet, via the capture point. Small disturbances are absorbed, larger ones become recovery steps, the largest a fall.
  * **falling**: the assist lets go. Muscles drive a protective pose: the free hand reaches for the landing spot, the head tucks, the saw is held away and the knees soften. Hard impacts cut muscle strength.
  * **down**: she relaxes and settles.
  * **getting up**: IK-solved reference poses for face-down and face-up recovery (`RecoveryPoses`) become muscle targets while the assist ramps in.
  * **blending**: a short hand-back to the animation.
* `SimSettings` with `assets/sim/warden.properties`: every tunable (body, solver, muscles, balance, reactions, quality) without code changes.
* Events: `onImpact`, `onStumble`, `onFall`, `onBalanceLost`, `onBalanceRestored`, `onRecover`, `onMuscleFatigue`, `onMovementStateChanged`. Footsteps, jumps and landings come from the locomotion.

**Debug and testing:**
* **In game:**
  * PUSH applies a random-strength push from the camera's direction.
  * RAGDOLL drops her.
  * DEBUG shows the skeleton capsules, joint anchors with muscle torque, assist targets, contacts, centre of mass and velocity, support polygon and capture point. It also shows live numbers: mode, ms per physics step, contacts, anchor error, strength, joint error, fatigue, centre-of-mass height and speed, stability margin, support area and kinetic energy.
* **Tests:** `app/src/test/.../CharacterSimTest.java` runs in CI (`gradle testDebugUnitTest`). It covers:
  * standing idle
  * a powered hold that must stay together
  * graded pushes (a light push is absorbed, a heavy one knocks her down, harder pushes destabilise more)
  * falls forward and backward that must get up within bounds (energy, floor penetration, knee range)
  * climbing stairs with feet on the steps
  * four simultaneous ragdolls
  * settings parsing

**Not yet done:**
* Fully physical locomotion: balance through leg torques instead of the assist.
* A dedicated stepping-strategy planner. Recovery steps currently come from nudging the locomotion.
* Spine segments beyond the lumbar joint.
* Sideways falls and side-lying get-ups.
* Moving platforms (there are none in the level).
* Enemy and NPC hit reactions (there are no enemies yet). `ActiveRagdoll.applyImpact(body, point, impulse)` is the hook.
* On-device profiling: the timings above are JVM numbers.
