/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import javax.annotation.Nullable;

/** Helper methods to run scoped sync operations throwing checked exceptions. */
final class SyncScope {

  private static final Logger logger = Logger.getInstance(SyncScope.class);

  static class SyncFailedException extends Exception {}

  static class SyncCanceledException extends Exception {}

  interface ScopedSyncOperation {
    void execute(BlazeContext context) throws SyncFailedException, SyncCanceledException;
  }

  /** Runs a scoped operation with the given {@link TimingScope} attached. */
  static void runWithTiming(
      @Nullable BlazeContext parentContext,
      ScopedSyncOperation operation,
      TimingScope timingScope) {
    ScopedSyncOperation withTiming =
        context -> {
          context.push(timingScope);
          operation.execute(context);
        };
    push(parentContext, withTiming);
  }

  /**
   * Runs a scoped operation in a new nested scope, handling checked sync exceptions appropriately.
   */
  static void push(@Nullable BlazeContext parentContext, ScopedSyncOperation scopedOperation) {
    BlazeContext context = new BlazeContext(parentContext);
    try {
      scopedOperation.execute(context);
    } catch (SyncCanceledException e) {
      context.setCancelled();
    } catch (SyncFailedException e) {
      context.setHasError();
    } catch (ProcessCanceledException e) {
      context.setCancelled();
      throw e;
    } catch (Throwable e) {
      context.setHasError();
      logger.error(e);
      throw e;
    } finally {
      context.endScope();
    }
  }
}
