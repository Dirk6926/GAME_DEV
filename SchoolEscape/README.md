# School Escape — first-person 3D horror game (pure Java)

You are **Dirk**, trapped in a dark school at night. Find the key to Room 404 and escape —
but something is walking the halls.  No libraries: only Java (Swing + software 3D raycaster).

## Run in VS Code (step by step)
1. Install **JDK 17 or 21** (https://adoptium.net) and **VS Code**.
2. In VS Code install the extension **Extension Pack for Java**.
3. `File > Open Folder…` and choose this `SchoolEscape` folder.
4. Open `Main.java` and click **Run** (above `main`) or press **F5**.
5. Click inside the game window to capture the mouse, then play.

Or from a terminal: `javac -d out *.java && java -cp out Main` (or `run.sh` / `run.bat`).

## Controls
W A S D move · Mouse look · F interact · Shift run · Ctrl quiet · C crouch · Space jump ·
T flashlight · 1–7 / wheel hotbar · click or Q use item · Tab inventory · J journal · Esc pause.

## Goal
Room 103 drawer → Staff Room key → Staff Room (south hall) → Room 404 key → Room 404 (north end).
The shadow figure hears you (running is loud) and flees from your flashlight beam.

## Java concepts and where they are
- **Class / Encapsulation**: `Player` (private fields, validated methods), `Inventory`, `Light`
- **Abstract + Inheritance**: `GameObject` → `Item` / `Prop` / `Entity`; `Prop` → `Searchable` → `LockerSpot`, `DeskDrawer`; `Door` → `EscapeDoor`
- **Polymorphism**: `Item.use()`, `Prop.onInteract()`, `Entity.think()`, `Door.onUnlocked()`; `Interactable` interface
- **Array**: `World.tiles`, patrol waypoints in `Stalker`, textures, z-buffer in `Renderer`
- **String**: dialogue, notes, door labels, objectives
- **Linked List**: `MyLinkedList` (inventory); **Stack**: `MyStack` (game states, BFS path); **Queue**: `MyQueue` (dialogue lines, BFS, stalker route); **Tree**: `MyTree` (journal sorted by note number)
