/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.incremental;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemService;
import android.content.Context;
import android.content.pm.DataLoaderParams;
import android.os.RemoteException;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Provides operations to open or create an IncrementalStorage, using IIncrementalService
 * service. Example Usage:
 *
 * <blockquote><pre>
 * IncrementalManager manager = (IncrementalManager) getSystemService(Context.INCREMENTAL_SERVICE);
 * IncrementalStorage storage = manager.openStorage("/path/to/incremental/dir");
 * </pre></blockquote>
 *
 * @hide
 */
@SystemService(Context.INCREMENTAL_SERVICE)
public final class IncrementalManager {
    private static final String TAG = "IncrementalManager";

    public static final int CREATE_MODE_TEMPORARY_BIND =
            IIncrementalService.CREATE_MODE_TEMPORARY_BIND;
    public static final int CREATE_MODE_PERMANENT_BIND =
            IIncrementalService.CREATE_MODE_PERMANENT_BIND;
    public static final int CREATE_MODE_CREATE =
            IIncrementalService.CREATE_MODE_CREATE;
    public static final int CREATE_MODE_OPEN_EXISTING =
            IIncrementalService.CREATE_MODE_OPEN_EXISTING;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"CREATE_MODE_"}, value = {
            CREATE_MODE_TEMPORARY_BIND,
            CREATE_MODE_PERMANENT_BIND,
            CREATE_MODE_CREATE,
            CREATE_MODE_OPEN_EXISTING,
    })
    public @interface CreateMode {
    }

    private final @Nullable IIncrementalService mService;
    @GuardedBy("mStorages")
    private final SparseArray<IncrementalStorage> mStorages = new SparseArray<>();

    public IncrementalManager(IIncrementalService service) {
        mService = service;
    }

    /**
     * Returns a storage object given a storage ID.
     *
     * @param storageId The storage ID to identify the storage object.
     * @return IncrementalStorage object corresponding to storage ID.
     */
    // TODO(b/136132412): remove this
    @Nullable
    public IncrementalStorage getStorage(int storageId) {
        synchronized (mStorages) {
            return mStorages.get(storageId);
        }
    }

    /**
     * Opens or create an Incremental File System mounted directory and returns an
     * IncrementalStorage object.
     *
     * @param path                Absolute path to mount Incremental File System on.
     * @param params              IncrementalDataLoaderParams object to configure data loading.
     * @param createMode          Mode for opening an old Incremental File System mount or creating
     *                            a new mount.
     * @param autoStartDataLoader Set true to immediately start data loader after creating storage.
     * @return IncrementalStorage object corresponding to the mounted directory.
     */
    @Nullable
    public IncrementalStorage createStorage(@NonNull String path,
            @NonNull DataLoaderParams params, @CreateMode int createMode,
            boolean autoStartDataLoader) {
        try {
            final int id = mService.createStorage(path, params.getData(), createMode);
            if (id < 0) {
                return null;
            }
            final IncrementalStorage storage = new IncrementalStorage(mService, id);
            synchronized (mStorages) {
                mStorages.put(id, storage);
            }
            if (autoStartDataLoader) {
                storage.startLoading();
            }
            return storage;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Opens an existing Incremental File System mounted directory and returns an IncrementalStorage
     * object.
     *
     * @param path Absolute target path that Incremental File System has been mounted on.
     * @return IncrementalStorage object corresponding to the mounted directory.
     */
    @Nullable
    public IncrementalStorage openStorage(@NonNull String path) {
        try {
            final int id = mService.openStorage(path);
            if (id < 0) {
                return null;
            }
            final IncrementalStorage storage = new IncrementalStorage(mService, id);
            synchronized (mStorages) {
                mStorages.put(id, storage);
            }
            return storage;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Opens or creates an IncrementalStorage that is linked to another IncrementalStorage.
     *
     * @return IncrementalStorage object corresponding to the linked storage.
     */
    @Nullable
    public IncrementalStorage createStorage(@NonNull String path,
            @NonNull IncrementalStorage linkedStorage, @CreateMode int createMode) {
        try {
            final int id = mService.createLinkedStorage(
                    path, linkedStorage.getId(), createMode);
            if (id < 0) {
                return null;
            }
            final IncrementalStorage storage = new IncrementalStorage(mService, id);
            synchronized (mStorages) {
                mStorages.put(id, storage);
            }
            return storage;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Iterates through path parents to find the base dir of an Incremental Storage.
     *
     * @param file Target file to search storage for.
     * @return Absolute path which is a bind-mount point of Incremental File System.
     */
    @Nullable
    private Path getStoragePathForFile(File file) {
        File currentPath = new File(file.getParent());
        while (currentPath.getParent() != null) {
            IncrementalStorage storage = openStorage(currentPath.getAbsolutePath());
            if (storage != null) {
                return currentPath.toPath();
            }
            currentPath = new File(currentPath.getParent());
        }
        return null;
    }

    /**
     * Set up an app's code path. The expected outcome of this method is:
     * 1) The actual apk directory under /data/incremental is bind-mounted to the parent directory
     * of {@code afterCodeFile}.
     * 2) All the files under {@code beforeCodeFile} will show up under {@code afterCodeFile}.
     *
     * @param beforeCodeFile Path that is currently bind-mounted and have APKs under it.
     *                       Should no longer have any APKs after this method is called.
     *                       Example: /data/app/vmdl*tmp
     * @param afterCodeFile Path that should will have APKs after this method is called. Its parent
     *                      directory should be bind-mounted to a directory under /data/incremental.
     *                      Example: /data/app/~~[randomStringA]/[packageName]-[randomStringB]
     * @throws IllegalArgumentException
     * @throws IOException
     * TODO(b/147371381): add unit tests
     */
    public void renameCodePath(File beforeCodeFile, File afterCodeFile)
            throws IllegalArgumentException, IOException {
        final String beforeCodePath = beforeCodeFile.getAbsolutePath();
        final String afterCodePathParent = afterCodeFile.getParentFile().getAbsolutePath();
        if (!isIncrementalPath(beforeCodePath)) {
            throw new IllegalArgumentException("Not an Incremental path: " + beforeCodePath);
        }
        final String afterCodePathName = afterCodeFile.getName();
        final Path apkStoragePath = Paths.get(beforeCodePath);
        if (apkStoragePath == null || apkStoragePath.toAbsolutePath() == null) {
            throw new IOException("Invalid source storage path for: " + beforeCodePath);
        }
        final IncrementalStorage apkStorage =
                openStorage(apkStoragePath.toAbsolutePath().toString());
        if (apkStorage == null) {
            throw new IOException("Failed to retrieve storage from Incremental Service.");
        }
        final IncrementalStorage linkedApkStorage = createStorage(afterCodePathParent, apkStorage,
                IncrementalManager.CREATE_MODE_CREATE
                        | IncrementalManager.CREATE_MODE_PERMANENT_BIND);
        if (linkedApkStorage == null) {
            throw new IOException("Failed to create linked storage at dir: " + afterCodePathParent);
        }
        linkFiles(apkStorage, beforeCodeFile, "", linkedApkStorage, afterCodePathName);
        apkStorage.unBind(beforeCodePath);
    }

    /**
     * Recursively set up directories and link all the files from source storage to target storage.
     *
     * @param sourceStorage The storage that has all the files and directories underneath.
     * @param sourceAbsolutePath The absolute path of the directory that holds all files and dirs.
     * @param sourceRelativePath The relative path on the source directory, e.g., "" or "lib".
     * @param targetStorage The target storage that will have the same files and directories.
     * @param targetRelativePath The relative path to the directory on the target storage that
     *                           should have all the files and dirs underneath,
     *                           e.g., "packageName-random".
     * @throws IOException When makeDirectory or makeLink fails on the Incremental File System.
     */
    private void linkFiles(IncrementalStorage sourceStorage, File sourceAbsolutePath,
            String sourceRelativePath, IncrementalStorage targetStorage,
            String targetRelativePath) throws IOException {
        targetStorage.makeDirectory(targetRelativePath);
        final File[] entryList = sourceAbsolutePath.listFiles();
        for (int i = 0; i < entryList.length; i++) {
            final File entry = entryList[i];
            final String entryName = entryList[i].getName();
            final String sourceEntryRelativePath =
                    sourceRelativePath.isEmpty() ? entryName : sourceRelativePath + "/" + entryName;
            final String targetEntryRelativePath = targetRelativePath + "/" + entryName;
            if (entry.isFile()) {
                sourceStorage.makeLink(
                        sourceEntryRelativePath, targetStorage, targetEntryRelativePath);
            } else if (entry.isDirectory()) {
                linkFiles(sourceStorage, entry, sourceEntryRelativePath, targetStorage,
                        targetEntryRelativePath);
            }
        }
    }

    /**
     * Closes a storage specified by the absolute path. If the path is not Incremental, do nothing.
     * Unbinds the target dir and deletes the corresponding storage instance.
     */
    public void closeStorage(@NonNull String path) {
        try {
            final int id = mService.openStorage(path);
            if (id < 0) {
                return;
            }
            mService.deleteStorage(id);
            synchronized (mStorages) {
                mStorages.remove(id);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Checks if Incremental is enabled
     */
    public static boolean isEnabled() {
        return nativeIsEnabled();
    }

    /**
     * Checks if path is mounted on Incremental File System.
     */
    public static boolean isIncrementalPath(@NonNull String path) {
        return nativeIsIncrementalPath(path);
    }

    /* Native methods */
    private static native boolean nativeIsEnabled();
    private static native boolean nativeIsIncrementalPath(@NonNull String path);
}
