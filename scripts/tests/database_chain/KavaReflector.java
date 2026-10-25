package h.Hchat.utils;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
public final class KavaReflector {
 public static Class<?> loadClass(String name,ClassLoader loader)throws ClassNotFoundException {return Class.forName(name,false,loader);}
 public static Method accessible(Method method){method.setAccessible(true);return method;}
 public static Method findDeclaredMethod(Class<?> type,String name,Class<?>... args)throws NoSuchMethodException{return accessible(type.getDeclaredMethod(name,args));}
 public static Object invoke(Method method,Object owner,Object... args)throws ReflectiveOperationException{return method.invoke(owner,args);}
 public static List<Method> declaredMethods(Class<?> type){return Arrays.asList(type.getDeclaredMethods());}
}
