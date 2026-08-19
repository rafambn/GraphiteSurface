#import "EngineBridge.h"
#import <objc/message.h>
#import <objc/runtime.h>

static Class GSEngineClass(void) {
    return objc_getClass("GraphiteEngineGraphiteEngineViewKt");
}

UIView *GSEngineCreateView(void) {
    Class c = GSEngineClass();
    if (c == NULL) {
        return NULL;
    }
    SEL s = sel_registerName("graphiteEngineCreateView");
    return ((UIView *(*)(id, SEL))objc_msgSend)(c, s);
}

void GSEngineDisposeView(UIView *view) {
    Class c = GSEngineClass();
    if (c == NULL || view == NULL) {
        return;
    }
    SEL s = sel_registerName("graphiteEngineDisposeViewView:");
    ((void (*)(id, SEL, UIView *))objc_msgSend)(c, s, view);
}
