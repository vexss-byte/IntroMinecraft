# LHC Member Intro (Fabric, Minecraft 26.3)

Cinematic one-by-one member introduction. Server-side logic + optional client HUD, one jar.

## Build
Requires JDK 25 and Gradle 9.x (generate the wrapper with `gradle wrapper --gradle-version 9.4.1`).
    ./gradlew build        ->  build/libs/lhc-intro-1.0.0.jar

Install the jar + Fabric API (0.160.5+26.3) on the SERVER.
Also install it on the CLIENT of whoever records/watches, to get the bottom-left info card.
Clients without the mod still get camera, big title and quote subtitle; the card becomes an action-bar line.

## Workflow
1. Stand on stage, face the direction members should face when posing:   /lhc setcenter
2. Stand where member #1 should appear:                                   /lhc setentrance
   (optional path bends)                                                  /lhc addwaypoint
3. /lhc addplayer NevinGaming [WALK|STROLL|RUN|SPRINT|JUMP|BUNNY] [pose] [particle]
4. Fine tune:  /lhc set NevinGaming role Founder
               /lhc set NevinGaming desc Builder of the base
               /lhc set NevinGaming quote WOIII LAPERGANGG!
               /lhc set NevinGaming camera TRACK_FRONT   /lhc set NevinGaming posecamera ORBIT
               /lhc set NevinGaming speed 6.0            /lhc set NevinGaming poseseconds 10
5. /lhc preview NevinGaming   (single member, no opening)   then   /lhc playintro [fromNumber]
Other: /lhc list, /lhc skip, /lhc stop, /lhc reloadconfig, /lhc setstart <name>, /lhc clearwaypoints <name>

Members must be online. Everyone else online (and you) become spectator viewers and are restored afterwards.
Config: config/lhc_intro.json (all positions, timings, camera defaults, seat height, external pose commands).
