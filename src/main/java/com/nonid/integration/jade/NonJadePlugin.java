package com.nonid.integration.jade;

import com.nonid.GenderHolder;
import com.nonid.NeedsOfNature;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public final class NonJadePlugin implements IWailaPlugin {
   private static final Identifier GENDER = NeedsOfNature.id("gender");

   public void registerClient(IWailaClientRegistration registration) {
      registration.addConfig(GENDER, true);
      registration.registerEntityComponent(NonJadePlugin.GenderProvider.INSTANCE, MobEntity.class);
      registration.markAsClientFeature(GENDER);
   }

   private enum GenderProvider implements IEntityComponentProvider {
      INSTANCE;

      public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
         String symbol = genderSymbol(accessor.getEntity());
         if (!symbol.isEmpty()) {
            tooltip.append(Text.literal(" " + symbol), NonJadePlugin.GENDER);
         }
      }

      public Identifier getUid() {
         return NonJadePlugin.GENDER;
      }

      public int getDefaultPriority() {
         return -10099;
      }

      private static String genderSymbol(Entity entity) {
         if (entity instanceof GenderHolder holder) {
            return switch (holder.getGenderMask() & 3) {
               case 1 -> "♂";
               case 2 -> "♀";
               case 3 -> "♂♀";
               default -> "";
            };
         } else {
            return "";
         }
      }
   }
}
