#ifndef GS_ENGINE_H
#define GS_ENGINE_H

#import <UIKit/UIKit.h>
#import "GSTypes.h"

/* Render mode: 0 = Continuously, 1 = WhenDirty. */
@interface GraphiteEngineGraphiteEngineView_iosKt : NSObject

+ (UIView *)gsCreateViewRenderMode:(int)renderMode;
+ (void)gsDisposeViewView:(UIView *)view;

+ (void)gsStartRenderingView:(UIView *)view callback:(GSFrameCallback)callback;
+ (void)gsStopRenderingView:(UIView *)view;
+ (void)gsRequestRenderView:(UIView *)view;

/* Drawing operations are valid only from GSFrameCallback. */
+ (void)gsClearView:(UIView *)view color:(unsigned)color; /* 0xAARRGGBB */
+ (void)gsSaveView:(UIView *)view;
+ (void)gsRestoreView:(UIView *)view;
+ (void)gsTranslateView:(UIView *)view x:(float)x y:(float)y;
+ (void)gsRotateView:(UIView *)view degrees:(float)degrees;
+ (void)gsBeginPathView:(UIView *)view;
+ (void)gsMoveToView:(UIView *)view x:(float)x y:(float)y;
+ (void)gsLineToView:(UIView *)view x:(float)x y:(float)y;
+ (void)gsClosePathView:(UIView *)view;
+ (void)gsDrawPathView:(UIView *)view color:(unsigned)color antiAlias:(int)antiAlias;

@end

#endif /* GS_ENGINE_H */
