# **WorldMood**

![worldmood-plugin](https://i.postimg.cc/cLhkQg7D/worldmood-plugin.jpg)

## **What is WorldMood?**

WorldMood cycles your server through seven distinct **moods** — timed events that change how the
world actually plays, not just how it looks. A Blood Moon makes the night genuinely dangerous.
Calm Skies stops hostile mobs spawning at all. Void Tension makes creatures blink through space.

Each mood announces itself, runs for a set time, shows a boss-bar countdown, and cleans up after
itself. Everything is toggleable and tunable in `config.yml`.

## **✨ The seven moods**

| Mood | What it does | When |
| :---- | :---- | :---- |
| 🩸 **Blood Moon** | Hostile mobs spawn stronger, faster and more numerous under a red sky. Triggers Blood Frenzy, Crimson Lightning and Horde Surge events. | Night only |
| ☀️ **Calm Skies** | Hostile spawning suppressed, storms cleared, players regenerate. | Day only |
| 🔥 **Infernal Heat** | The sun burns exposed players. Seek shade, water or protection. | Day only |
| 🌬️ **Prosperous Winds** | Richer ore veins and better drops from fallen mobs. | Day only |
| 🍀 **Lucky Day** | Improved fortune on loot and trades. | Day only |
| 🌑 **Shadow Veil** | Flickering blindness and invisibility, ambient dread, true darkness in caves. | Night only |
| 🌀 **Void Tension** | Reality strains — mobs turn void-touched, move unnaturally and teleport. | Any |

## **🎮 Commands & Permissions**

The main command is `/worldmood`, aliased to `/wm`.

| Command | Description | Permission |
| :---- | :---- | :---- |
| `/wm start <mood>` | Force-starts a specific mood. | `worldmood.admin` |
| `/wm stop` | Stops the current mood, starts nothing new. | `worldmood.admin` |
| `/wm skip` | Stops the current mood and rolls a new one. | `worldmood.admin` |
| `/wm list` | Shows status and every configured mood. | `worldmood.admin` |
| `/wm info` | Alias for `list`. | `worldmood.admin` |
| `/wm reload` | Reloads `config.yml`. | `worldmood.admin` |

| Permission Node | Description | Default |
| :---- | :---- | :---- |
| `worldmood.admin` | Grants access to all `/worldmood` commands. | `op` |

## **⚙️ Which download do I need?**

There are two jars. **Pick the one that matches your server — do not install both.**

| Your Minecraft version | Download | Needs |
| :---- | :---- | :---- |
| **1.20 – 1.21.x** | `WorldMood-x.y.z.jar` | Java 17+ |
| **1.16.5 – 1.19.x** | `WorldMood-x.y.z-legacy.jar` | Java 8+ |

Same features, same config, same commands. They exist as two files because servers older than 1.18
run on Java 8–16, which cannot load modern Java bytecode at all — one jar physically cannot cover
both eras.

On older servers a few *visual* extras are unavailable and the plugin substitutes the nearest
equivalent automatically: the Darkness effect and the Sonic Boom particle (1.19+), the colour-fading
dust particle (1.17+), and villager trade discounts (1.18+). Everything else behaves identically,
and the plugin logs exactly what your version does and does not support when it starts.

Spigot and Paper are both supported. No dependencies, no database, no external calls.

## **📥 Installation**

1. Download the jar for your version from the [Releases](https://github.com/Rex-Micky/WorldMood-Plugin/releases) page.
2. Drop it into your server's `/plugins` folder.
3. Restart the server.
4. Edit `/plugins/WorldMood/config.yml` to taste, then `/wm reload`.

## **🛡️ A note on your world's settings**

Two moods change settings that are stored in your **world save**, not in the plugin: Calm Skies
temporarily turns off the `doMobSpawning` game rule, and Blood Moon and Void Tension temporarily
adjust the world border to tint the sky.

WorldMood writes these down to `plugins/WorldMood/pending-world-state.yml` *before* changing them,
and restores them on startup if the server ever goes down mid-mood. **If you ever remove the plugin
while a mood is active, start it once more and stop the mood cleanly** — or check that file and set
those values back by hand.

## **🔧 Building from source**

```bash
git clone https://github.com/Rex-Micky/WorldMood-Plugin.git
cd WorldMood-Plugin
./gradlew build      # jar lands in build/libs/
```

## **💬 Support & Suggestions**

Found a bug or have an idea for a new mood?

* Open an issue on this [repository](https://github.com/Rex-Micky/WorldMood-Plugin/issues).
* Join the [Discord](https://discord.gg/9bmgP7BpH8).

I also build **custom Minecraft plugins** to order — economy systems, cosmetics, custom mechanics
and QoL tools. Portfolio: [studio.rexmicky.dev](https://studio.rexmicky.dev)
