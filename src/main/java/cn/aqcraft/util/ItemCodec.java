package cn.aqcraft.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** ItemStack ↔ Base64（用于网页离线购买物品的暂存与发放） */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack item) {
        if (item == null) return null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeObject(item);
            out.flush();
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Throwable t) {
            return null;
        }
    }

    public static ItemStack decode(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try (ByteArrayInputStream bin = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bin)) {
            Object o = in.readObject();
            return o instanceof ItemStack ? (ItemStack) o : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
