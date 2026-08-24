#ifndef GS_ENGINE_H
#define GS_ENGINE_H

#import <UIKit/UIKit.h>
#import "GSTypes.h"

/* Render mode: 0 = Continuous, 1 = OnDemand. */
@interface GraphiteEngineGraphiteEngineView_iosKt : NSObject

+ (UIView *)gsCreateViewRenderMode:(int)renderMode;
+ (void)gsDisposeViewView:(UIView *)view;

+ (void)gsStartRenderingView:(UIView *)view callback:(GSFrameCallback)callback failureCallback:(GSFailureCallback)failureCallback;
+ (void)gsStopRenderingView:(UIView *)view;
+ (void)gsRequestRenderView:(UIView *)view;
+ (int)gsDrawableWidthView:(UIView *)view;
+ (int)gsDrawableHeightView:(UIView *)view;

/* Drawing operations are valid only from GSFrameCallback. */
+ (void)gsClearView:(UIView *)view color:(unsigned)color; /* 0xAARRGGBB */
+ (void)gsSaveView:(UIView *)view;
+ (void)gsRestoreView:(UIView *)view;
+ (void)gsTranslateView:(UIView *)view x:(float)x y:(float)y;
+ (void)gsRotateView:(UIView *)view degrees:(float)degrees;
+ (void)gsConcatView:(UIView *)view m0:(float)m0 m1:(float)m1 m2:(float)m2 m3:(float)m3 m4:(float)m4 m5:(float)m5 m6:(float)m6 m7:(float)m7 m8:(float)m8 m9:(float)m9 m10:(float)m10 m11:(float)m11 m12:(float)m12 m13:(float)m13 m14:(float)m14 m15:(float)m15;
+ (void)gsClipRectView:(UIView *)view left:(float)left top:(float)top right:(float)right bottom:(float)bottom antiAlias:(int)antiAlias;
+ (void)gsBeginPathView:(UIView *)view;
+ (void)gsMoveToView:(UIView *)view x:(float)x y:(float)y;
+ (void)gsLineToView:(UIView *)view x:(float)x y:(float)y;
+ (void)gsClosePathView:(UIView *)view;
+ (void)gsDrawPathView:(UIView *)view color:(unsigned)color antiAlias:(int)antiAlias;
+ (void)gsDrawStyledPathView:(UIView *)view color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawRectView:(UIView *)view left:(float)left top:(float)top right:(float)right bottom:(float)bottom color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawRoundRectView:(UIView *)view left:(float)left top:(float)top right:(float)right bottom:(float)bottom radiusX:(float)radiusX radiusY:(float)radiusY color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawOvalView:(UIView *)view left:(float)left top:(float)top right:(float)right bottom:(float)bottom color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawCircleView:(UIView *)view x:(float)x y:(float)y radius:(float)radius color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawLineView:(UIView *)view x0:(float)x0 y0:(float)y0 x1:(float)x1 y1:(float)y1 color:(unsigned)color strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;

@end

#endif /* GS_ENGINE_H */
