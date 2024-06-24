package kr.lunaf.lunastructurebuilder;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.internal.annotation.Selection;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public final class LunaStructureBuilder extends JavaPlugin {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (command.getName().equalsIgnoreCase("exportstructure")) {
                if (!player.hasPermission("structurebuilder.export")) {
                    player.sendMessage("You don't have permission to use this command.");
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage("Usage: /exportstructure <name>");
                    return false;
                }
                exportStructure(player, args[0]);
                return true;


            } else if (command.getName().equalsIgnoreCase("buildstructure")) {
                if (!player.hasPermission("structurebuilder.build")) {
                    player.sendMessage("You don't have permission to use this command.");
                    return true;
                }
                if (args.length != 1) {
                    sender.sendMessage("Usage: /buildstructure <name>");
                    return false;
                }
                buildStructure(player, args[0]);
                return true;
            }
        }
        return false;
    }
    private boolean isAreaClear(Location baseLoc, int[] size) {
        for (int x = baseLoc.getBlockX(); x < baseLoc.getBlockX() + size[0]; x++) {
            for (int y = baseLoc.getBlockY(); y < baseLoc.getBlockY() + size[1]; y++) {
                for (int z = baseLoc.getBlockZ(); z < baseLoc.getBlockZ() + size[2]; z++) {
                    if (new Location(baseLoc.getWorld(), x, y, z).getBlock().getType() != Material.AIR) {
                        return false;
                    }
                }

            }
        }
        return true;
    }
    private void buildStructure(Player player, String name) {
        File file = new File(getDataFolder(), name + ".yml");
        if (!file.exists()) {
            player.sendMessage("File not found");
            return;
        }
        Location baseLoc = player.getLocation();
        try (Reader reader = new FileReader(file)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);

            int[] size = (int[]) data.get("size");
            if (isAreaClear(baseLoc, size)) {
                player.sendMessage("Cannot build structure: Area is not clear.");
                return;
            }
            Map<String, Map<String, Object>> blocks = (Map<String, Map<String, Object>>) data.get("blocks");

            for (Map.Entry<String, Map<String, Object>> entry : blocks.entrySet()) {
                String[] coords = entry.getKey().split(",");
                int dx = Integer.parseInt(coords[0]);
                int dy = Integer.parseInt(coords[1]);
                int dz = Integer.parseInt(coords[2]);
                Material material = Material.getMaterial((String) entry.getValue().get("material"));
                Location loc = baseLoc.clone().add(new Vector(dx, dy, dz));

                loc.getBlock().setType(material);

                if (entry.getValue().containsKey("lines")) {
                    Sign sign = (Sign) loc.getBlock().getState();
                    String[] lines = (String[]) entry.getValue().get("lines");
                    for (int i = 0; i < lines.length; i++) {
                        sign.setLine(i, lines[i]);
                    }
                    sign.update();
                } else if (entry.getValue().containsKey("inventory")) {
                    Inventory inventory = (Inventory) loc.getBlock().getState();
                    inventory.setContents((ItemStack[]) entry.getValue().get("inventory"));
                }

                Bukkit.getScheduler().runTaskLater(this, () -> {

                }, 2L);
            }
        } catch (Exception e) {
            player.sendMessage("Error building structure");
            return;
        }
    }
    private void exportStructure(Player player, String name) {
        WorldEditPlugin worldEdit = (WorldEditPlugin)getServer().getPluginManager().getPlugin("WorldEdit");
        if (worldEdit == null) {
            player.sendMessage("WorldEdit not found.");
            return;
        }
        Actor actor = BukkitAdapter.adapt(player);
        SessionManager sessionManager = worldEdit.getWorldEdit().getSessionManager();
        LocalSession localSession = sessionManager.get(actor);
        if (localSession == null) {
            player.sendMessage("WorldEdit session not found.");
            return;
        }
        World selectionWorld = localSession.getSelectionWorld();
        if (selectionWorld == null) {
            player.sendMessage("WorldEdit selection not found.");
            return;
        }
        Region selectionRegion;
        try {
            selectionRegion = localSession.getSelection(selectionWorld);
        } catch (Exception e) {
            player.sendMessage("Failed to get selection");
            return;
        }
        Location baseLoc = player.getLocation();
        int baseX = baseLoc.getBlockX();
        int baseY = baseLoc.getBlockY();
        int baseZ = baseLoc.getBlockZ();
        File file = new File(getDataFolder(), name + ".yml");
        try (FileWriter writer = new FileWriter(file)) {
            DumperOptions options = new DumperOptions();
            options.setIndent(4);
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);

            Map<String, Object> data = new HashMap<>();
            data.put("size", new int[]{selectionRegion.getWidth(), selectionRegion.getHeight(), selectionRegion.getLength()});

            Map<String, Map<String, Object>> blocks = new HashMap<>();
            for (int x = selectionRegion.getMinimumPoint().x(); x <= selectionRegion.getMaximumPoint().x(); x++) {
                for (int y = selectionRegion.getMinimumPoint().y(); y <= selectionRegion.getMaximumPoint().y(); y++) {
                    for (int z = selectionRegion.getMinimumPoint().z(); z <= selectionRegion.getMaximumPoint().z(); z++) {
                        Location loc = new Location(BukkitAdapter.adapt(selectionWorld), x, y, z);
                        Block block = loc.getBlock();
                        Material material = block.getType();
                        if (material != Material.AIR) {
                            String key = (x - baseX) + "," + (y - baseY) + "," + (z - baseZ);
                            Map<String, Object> blockData = new HashMap<>();
                            blockData.put("material", material.name());

                            BlockState state = block.getState();
                            if (state instanceof Sign) {
                                Sign sign = (Sign) state;
                                blockData.put("lines", sign.getLines());
                            } else if (state instanceof Inventory) {
                                Inventory inventory = (Inventory) state;
                                blockData.put("inventory", inventory.getContents());
                            }

                            blocks.put(key, blockData);
                        }
                    }

                }
            }
            data.put("blocks", blocks);
            yaml.dump(data, writer);
            player.sendMessage("Exported structure to " + file.getAbsolutePath());
        } catch (Exception e) {
            player.sendMessage("Failed to save structure");
            return;
        }
    }

}
