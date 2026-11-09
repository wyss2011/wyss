package com.nonid.client;

import java.util.Map;

/**
 * 将 afw_animdefs 的 content_tags 本地化为中文显示。
 */
public final class NonTagLocalizer {
   private static final Map<String, String> TAGS = Map.ofEntries(
      Map.entry("against_block", "靠方块"),
      Map.entry("against_wall", "靠墙"),
      Map.entry("blowjob", "口部侍奉"),
      Map.entry("cowgirl", "骑乘位"),
      Map.entry("doggy", "后入式"),
      Map.entry("forcing", "强制"),
      Map.entry("from_behind", "背后"),
      Map.entry("fullnelson", "背后锁抱"),
      Map.entry("handjob", "手部侍奉"),
      Map.entry("holding", "抱持"),
      Map.entry("mating_press", "交配压制"),
      Map.entry("missionary", "传教士式"),
      Map.entry("no_pen", "无插入"),
      Map.entry("on_a_block", "方块上"),
      Map.entry("on_a_wall", "靠墙"),
      Map.entry("on_back", "仰躺"),
      Map.entry("on_belly", "趴伏"),
      Map.entry("oral", "口部"),
      Map.entry("side", "侧位"),
      Map.entry("spitroast", "双人串烧"),
      Map.entry("stand_and_carry", "站立抱姿"),
      Map.entry("standing", "站立"),
      Map.entry("wall", "靠墙")
   );

   private NonTagLocalizer() {
   }

   public static String localize(String tag) {
      if (tag == null) {
         return "";
      }
      return TAGS.getOrDefault(tag, tag);
   }
}
