package rikka.shizuku.server.util;

import android.content.pm.PackageInfo;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import rikka.hidden.compat.PackageManagerApis;
import rikka.hidden.compat.PermissionManagerApis;

public class Android17Compat {

    private static final String TAG = "ShizukuAndroid17Compat";
    private static final int DEVICE_ID_DEFAULT = 0; // Context.DEVICE_ID_DEFAULT

    private static Object sPackageManager;
    private static Method sGetInstalledPackagesMethod;
    private static boolean sPackageManagerFallbackInit = false;

    private static Object sPermissionManager;
    private static Method sGrantRuntimePermissionMethod;
    private static Method sRevokeRuntimePermissionMethod;
    private static boolean sPermissionManagerFallbackInit = false;

    @SuppressWarnings("unchecked")
    public static List<PackageInfo> getInstalledPackages(long flags, int userId) {
        try {
            return PackageManagerApis.getInstalledPackagesNoThrow(flags, userId);
        } catch (NoSuchMethodError e) {
            if (!sPackageManagerFallbackInit) {
                try {
                    IBinder binder = ServiceManager.getService("package");
                    Class<?> stubClass = Class.forName("android.content.pm.IPackageManager$Stub");
                    sPackageManager = stubClass.getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);

                    for (Method method : sPackageManager.getClass().getMethods()) {
                        if ("getInstalledPackages".equals(method.getName())) {
                            sGetInstalledPackagesMethod = method;
                            break;
                        }
                    }
                } catch (Throwable ex) {
                    Log.e(TAG, "Android 17 fallback init for getInstalledPackages failed", ex);
                }
                sPackageManagerFallbackInit = true;
            }

            if (sGetInstalledPackagesMethod != null && sPackageManager != null) {
                try {
                    Class<?>[] paramTypes = sGetInstalledPackagesMethod.getParameterTypes();
                    Object[] args = new Object[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (paramTypes[i] == long.class) {
                            args[i] = flags;
                        } else if (paramTypes[i] == int.class) {
                            args[i] = userId;
                        } else {
                            args[i] = null;
                        }
                    }
                    
                    Object result = sGetInstalledPackagesMethod.invoke(sPackageManager, args);
                    if (result != null) {
                        Method getListMethod = result.getClass().getMethod("getList");
                        return (List<PackageInfo>) getListMethod.invoke(result);
                    }
                } catch (Throwable ex) {
                    Log.e(TAG, "Android 17 fallback for getInstalledPackages failed", ex);
                }
            }
            return new ArrayList<>();
        }
    }

    private static void initPermissionManagerFallback() {
        if (!sPermissionManagerFallbackInit) {
            try {
                IBinder binder = ServiceManager.getService("permissionmgr");
                Class<?> stubClass = Class.forName("android.permission.IPermissionManager$Stub");
                sPermissionManager = stubClass.getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);

                for (Method method : sPermissionManager.getClass().getMethods()) {
                    if ("grantRuntimePermission".equals(method.getName())) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length >= 3 && paramTypes[0] == String.class && paramTypes[1] == String.class) {
                            sGrantRuntimePermissionMethod = method;
                        }
                    } else if ("revokeRuntimePermission".equals(method.getName())) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length >= 3 && paramTypes[0] == String.class && paramTypes[1] == String.class) {
                            sRevokeRuntimePermissionMethod = method;
                        }
                    }
                }
            } catch (Throwable ex) {
                Log.e(TAG, "Android 17 fallback init for permission manager failed", ex);
            }
            sPermissionManagerFallbackInit = true;
        }
    }

    public static void grantRuntimePermission(String packageName, String permissionName, int userId) throws android.os.RemoteException {
        try {
            PermissionManagerApis.grantRuntimePermission(packageName, permissionName, userId);
        } catch (NoSuchMethodError e) {
            initPermissionManagerFallback();
            
            if (sGrantRuntimePermissionMethod != null && sPermissionManager != null) {
                try {
                    Class<?>[] paramTypes = sGrantRuntimePermissionMethod.getParameterTypes();
                    Object[] args = new Object[paramTypes.length];
                    args[0] = packageName;
                    args[1] = permissionName;
                    
                    if (paramTypes.length == 4 && paramTypes[2] == int.class && paramTypes[3] == int.class) {
                        args[2] = DEVICE_ID_DEFAULT; // deviceId
                        args[3] = userId; // userId
                    } else {
                        for (int i = 2; i < paramTypes.length; i++) {
                            if (paramTypes[i] == int.class) {
                                args[i] = userId;
                            }
                        }
                    }
                    sGrantRuntimePermissionMethod.invoke(sPermissionManager, args);
                } catch (Throwable ex) {
                    Log.e(TAG, "Android 17 fallback for grantRuntimePermission failed", ex);
                }
            }
        }
    }

    public static void revokeRuntimePermission(String packageName, String permissionName, int userId) throws android.os.RemoteException {
        try {
            PermissionManagerApis.revokeRuntimePermission(packageName, permissionName, userId);
        } catch (NoSuchMethodError e) {
            initPermissionManagerFallback();
            
            if (sRevokeRuntimePermissionMethod != null && sPermissionManager != null) {
                try {
                    Class<?>[] paramTypes = sRevokeRuntimePermissionMethod.getParameterTypes();
                    Object[] args = new Object[paramTypes.length];
                    args[0] = packageName;
                    args[1] = permissionName;
                    
                    if (paramTypes.length == 5 && paramTypes[2] == int.class && paramTypes[3] == int.class && paramTypes[4] == String.class) {
                        args[2] = DEVICE_ID_DEFAULT;
                        args[3] = userId;
                        args[4] = "shizuku";
                    } else if (paramTypes.length == 4 && paramTypes[2] == int.class && paramTypes[3] == int.class) {
                        args[2] = DEVICE_ID_DEFAULT;
                        args[3] = userId;
                    } else {
                        for (int i = 2; i < paramTypes.length; i++) {
                            if (paramTypes[i] == int.class) {
                                args[i] = userId;
                            }
                        }
                    }
                    sRevokeRuntimePermissionMethod.invoke(sPermissionManager, args);
                } catch (Throwable ex) {
                    Log.e(TAG, "Android 17 fallback for revokeRuntimePermission failed", ex);
                }
            }
        }
    }
}
