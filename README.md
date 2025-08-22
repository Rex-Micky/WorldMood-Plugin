# **WorldMood Plugin - BETA VERSION**

![worldmood-plugin](https://i.postimg.cc/cLhkQg7D/worldmood-plugin.jpg)

## **What is WorldMood?**

**Experience a revolutionary Minecraft survival experience where atmospheric events and world moods directly impact your gameplay.** WorldMood is an advanced plugin that transforms your server's environment with dynamic, thematic events—from the terrifying **BloodMoon** to the serene **CalmSkies**. Each mood introduces unique mechanics, challenges, and visual effects that completely transform traditional survival.

Inspired by concepts that tie the environment to survival, WorldMood makes the world itself a character in your players' stories. Are you ready to adapt and survive?

## **✨ Core Features**

* **Dynamic Mood System:** The world seamlessly transitions between different atmospheric moods, each with its own unique properties.  
* **Customizable Events:** Control which moods can occur, how often they happen, and how long they last.  
* **Unique Mood Mechanics:** Each mood is more than just a visual change. They introduce real gameplay effects:  
  * **🩸 BloodMoon:** A night of terror where hostile mobs are stronger, faster, and more numerous. A true test of survival.  
  * **🌬️ ProsperousWinds:** A bountiful period where crops grow faster and loot is more plentiful.  
  * **☀️ LuckyDay:** Fortune smiles upon you\! Players experience increased luck in fishing, enchanting, and finding rare items.  
  * **🔥 InfernalHeat:** The world heats up, causing negative effects for those without proper protection.  
  * **...and many more\!** Discover all the unique moods like `ShadowVeil`, `VoidTension`, and `CalmSkies`.  
* **Visual & Auditory Effects:** Each mood comes with custom particles, sounds, and screen effects to fully immerse your players.  
* **Highly Performant:** Designed to be lightweight and efficient, ensuring a smooth experience even on busy servers.

## **🎮 Commands & Permissions**

The main command for this plugin is `/worldmood` or `/wm`.

| Command | Description | Permission |
| :---- | :---- | :---- |
| `/wm start [mood]` | Manually starts a specific world mood. | `worldmood.admin` |
| `/wm stop` | Stops the current world mood and returns to normal. | `worldmood.admin` |
| `/wm list` | Lists all available world moods. | `worldmood.admin` |
| `/wm reload` | Reloads the plugin's configuration file. | `worldmood.admin` |
| `/wm skip` | Skips the current mood and starts the next one in the cycle. | `worldmood.admin` |

### **Permissions**

| Permission Node | Description | Default |
| :---- | :---- | :---- |
| `worldmood.admin` | Grants access to all `/worldmood` commands. | `op` |

## **⚙️ Installation**

1. Download the latest version of `WorldMood.jar` from the [Releases](https://github.com/Rex-Micky/WorldMood-Plugin/releases) page.  
2. Place the `.jar` file into your server's `/plugins` folder.  
3. Restart or reload your server.  
4. The plugin will generate its configuration files in `/plugins/WorldMood/`. You can edit the `config.yml` to customize the plugin to your liking.

## **🔧 Configuration**

The `config.yml` file is heavily commented and allows you to configure:

* The duration of each mood.  
* The chance for each mood to occur.  
* Custom messages and broadcasts.  
* Enable or disable specific moods entirely.

## **💬 Support & Suggestions**

Got a question, found a bug, or have a cool idea for a new mood? Feel free to:

* Open an issue on this [GitHub repository](https://github.com/Rex-Micky/WorldMood-Plugin/issues).  
* Join my [Discord Server](https://discord.gg/9bmgP7BpH8) for support.

