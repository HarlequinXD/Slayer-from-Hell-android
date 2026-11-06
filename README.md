# Slayer from Hell (Android)

A first-person shooter test scene in the style of Quake/Doom, written from scratch in Java with OpenGL ES 3.0.

* **PBR rendering**: GGX/Smith/Schlick shading of albedo, normal, height, ambient-occlusion, roughness, metalness and emission maps, ACES tone mapping, fog.
* **Parallax occlusion mapping**: an adaptive ray march through each material's height map with binary-search refinement, plus soft self-shadows toward the nearest lights.
* **Procedural materials**: every texture is generated at start-up (hell brick, riveted metal panels, cracked floor tiles, diamond plate, concrete, crates, light panels, carved pillars, flowing lava, gunmetal, walnut, leather, fabric, brass, shell hulls).
* **Test level**: a start room with pillars and crates, a corridor, a 9 m hall with stairs up to a balcony and a lava pit, and a store room. Lights are masked per cell so they don't shine through walls.
* **Quake-style controller**: ground friction/acceleration, air control (strafe-jumping works), jumping, stepping up stairs, head bob, landing dip, recoil kick.
* **Double-barrel shotgun, baked from high poly**: `tools/bake/GunBaker.java` sculpts a 5.5-million-triangle side-by-side shotgun (hollow bores and chambers, serrated rib, brass bead, case-hardened action with scroll engraving, slotted screws, hinge pin, top lever, safety, triggers and guard, chequered walnut, ribbed butt pad) and bakes it onto a 744-triangle game mesh (UV-unwrapped into 79 charts on a 2048² atlas, 0.39 mm per texel): normal, height, ambient occlusion, albedo, roughness and metalness maps (`app/src/main/assets`). Rebuild with `javac tools/bake/GunBaker.java && java -Xmx4g -cp tools/bake GunBaker app/src/main/assets /tmp/preview [mesh]`.
* **The warden, an original armoured heroine baked from high poly**: `tools/bake/HeroBaker.java` sculpts a part-based figure (3.3 million triangles when meshed at 1.5 mm): charcoal undersuit, bone-white ceramic plates with panel grooves, rivets, soot and chipped edges, dark steel trim, a crested helmet with a glowing amber visor slit, a long braid, leather belt, pouches and gauntlets, a crimson sash, and a glaive whose obsidian blade glows through molten cracks. Each part's low poly is lofted through rings fitted to the sculpt by ray casting (2,446 triangles, 41 UV charts, 2048² atlas at 1.1 mm per texel) and baked to normal, height, AO, albedo, roughness, metalness and emission maps. She stands in the start room facing the spawn point. Rebuild with `javac tools/bake/HeroBaker.java && java -Xmx6g -cp tools/bake HeroBaker app/src/main/assets /tmp/preview [low|prev|high|obj]`.
* **Break-action**: fire the right then the left barrel (heavy kick, 9 buckshot pellets each), break it open on the hinge, the extractors throw the fired cases over your shoulder, the left hand loads fresh shells and snaps it shut. Bullet holes, sparks, dust and splinters, muzzle flash light and smoke.
* **Sound**: all effects are synthesised in code.

Controls: left side = move stick, right side = drag to look (also while holding Fire), FIRE / RELOAD / JUMP buttons (hold Jump to bunny-hop).

The APK is built by GitHub Actions (Actions → latest run → artifact `SlayerFromHell-debug`).
