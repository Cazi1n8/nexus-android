// Derived from the default logging_factory genrule in MediaPipe v0.10.35.
// See MEDIAPIPE-RUNTIME-NOTICES.txt for provenance and the explicit modification.
package com.google.mediapipe.tasks.core.logging;

import android.content.Context;

public final class TasksStatsLoggerFactory {
  public static TasksStatsLogger create(
      Context context, String taskNameStr, String taskRunningModeStr) {
    return TasksStatsDummyLogger.create(context, taskNameStr, taskRunningModeStr);
  }
}
