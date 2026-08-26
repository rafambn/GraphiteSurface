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

/* Recorder handles are thread-confined. Recording handles may be inserted repeatedly. */
+ (unsigned long long)gsCreateRecorderView:(UIView *)view;
+ (void)gsDisposeRecorderRecorder:(unsigned long long)recorder;
+ (void)gsBeginRecordingRecorder:(unsigned long long)recorder width:(int)width height:(int)height;
+ (unsigned long long)gsFinishRecordingRecorder:(unsigned long long)recorder;
+ (void)gsDisposeRecordingRecording:(unsigned long long)recording;
+ (void)gsInsertRecordingView:(UIView *)view recording:(unsigned long long)recording translationX:(int)translationX translationY:(int)translationY clipLeft:(int)clipLeft clipTop:(int)clipTop clipRight:(int)clipRight clipBottom:(int)clipBottom hasClip:(int)hasClip;

/* Target 0 selects the active presentation callback. Other targets select worker recorders. */
+ (void)gsClearView:(UIView *)view target:(unsigned long long)target color:(unsigned)color; /* 0xAARRGGBB */
+ (void)gsSaveView:(UIView *)view target:(unsigned long long)target;
+ (void)gsRestoreView:(UIView *)view target:(unsigned long long)target;
+ (void)gsTranslateView:(UIView *)view target:(unsigned long long)target x:(float)x y:(float)y;
+ (void)gsRotateView:(UIView *)view target:(unsigned long long)target degrees:(float)degrees;
+ (void)gsConcatView:(UIView *)view target:(unsigned long long)target m0:(float)m0 m1:(float)m1 m2:(float)m2 m3:(float)m3 m4:(float)m4 m5:(float)m5 m6:(float)m6 m7:(float)m7 m8:(float)m8 m9:(float)m9 m10:(float)m10 m11:(float)m11 m12:(float)m12 m13:(float)m13 m14:(float)m14 m15:(float)m15;
+ (void)gsClipRectView:(UIView *)view target:(unsigned long long)target left:(float)left top:(float)top right:(float)right bottom:(float)bottom antiAlias:(int)antiAlias;
+ (void)gsBeginPathView:(UIView *)view target:(unsigned long long)target;
+ (void)gsSetPathFillTypeView:(UIView *)view target:(unsigned long long)target fillType:(int)fillType;
+ (void)gsMoveToView:(UIView *)view target:(unsigned long long)target x:(float)x y:(float)y;
+ (void)gsLineToView:(UIView *)view target:(unsigned long long)target x:(float)x y:(float)y;
+ (void)gsQuadToView:(UIView *)view target:(unsigned long long)target x1:(float)x1 y1:(float)y1 x2:(float)x2 y2:(float)y2;
+ (void)gsConicToView:(UIView *)view target:(unsigned long long)target x1:(float)x1 y1:(float)y1 x2:(float)x2 y2:(float)y2 weight:(float)weight;
+ (void)gsCubicToView:(UIView *)view target:(unsigned long long)target x1:(float)x1 y1:(float)y1 x2:(float)x2 y2:(float)y2 x3:(float)x3 y3:(float)y3;
+ (void)gsClosePathView:(UIView *)view target:(unsigned long long)target;
+ (void)gsDrawPathView:(UIView *)view target:(unsigned long long)target color:(unsigned)color antiAlias:(int)antiAlias;
+ (void)gsDrawStyledPathView:(UIView *)view target:(unsigned long long)target color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawRectView:(UIView *)view target:(unsigned long long)target left:(float)left top:(float)top right:(float)right bottom:(float)bottom color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawRoundRectView:(UIView *)view target:(unsigned long long)target left:(float)left top:(float)top right:(float)right bottom:(float)bottom radiusX:(float)radiusX radiusY:(float)radiusY color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawOvalView:(UIView *)view target:(unsigned long long)target left:(float)left top:(float)top right:(float)right bottom:(float)bottom color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawCircleView:(UIView *)view target:(unsigned long long)target x:(float)x y:(float)y radius:(float)radius color:(unsigned)color stroke:(int)stroke strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;
+ (void)gsDrawLineView:(UIView *)view target:(unsigned long long)target x0:(float)x0 y0:(float)y0 x1:(float)x1 y1:(float)y1 color:(unsigned)color strokeWidth:(float)strokeWidth antiAlias:(int)antiAlias;

@end

#endif /* GS_ENGINE_H */
