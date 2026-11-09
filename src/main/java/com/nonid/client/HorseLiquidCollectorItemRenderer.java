package com.nonid.client;

import com.nonid.item.HorseLiquidCollectorItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class HorseLiquidCollectorItemRenderer extends GeoItemRenderer<HorseLiquidCollectorItem> {
   public HorseLiquidCollectorItemRenderer() {
      super(new HorseLiquidCollectorItemGeoModel());
   }
}
