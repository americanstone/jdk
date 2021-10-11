/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
#ifndef SHARE_LOGGING_LOGFILESTREAMOUTPUT_HPP
#define SHARE_LOGGING_LOGFILESTREAMOUTPUT_HPP

#include "logging/logDecorators.hpp"
#include "logging/logOutput.hpp"
#include "runtime/os.hpp"
#include "runtime/semaphore.hpp"
#include "utilities/globalDefinitions.hpp"

class LogDecorations;

class LogFileStreamInitializer {
 public:
  LogFileStreamInitializer();
};

// Ensure the default log streams have been initialized (stdout, stderr) using the static initializer below
static LogFileStreamInitializer log_stream_initializer;

// Base class for all FileStream-based log outputs.
class LogFileStreamOutput : public LogOutput {
 private:
  static const char* const FoldMultilinesOptionKey;
  bool                _fold_multilines;
  bool                _write_error_is_shown;
  int write_internal(const char* msg);
 protected:
  FILE*               _stream;
  // Semaphore used for synchronizing file rotations and writes
  Semaphore           _stream_semaphore;

  size_t              _decorator_padding[LogDecorators::Count];

  LogFileStreamOutput(FILE *stream) : _fold_multilines(false), _write_error_is_shown(false), _stream(stream), _stream_semaphore(1) {
    for (size_t i = 0; i < LogDecorators::Count; i++) {
      _decorator_padding[i] = 0;
    }
  }

  int write_decorations(const LogDecorations& decorations);
 public:
  virtual bool set_option(const char* key, const char* value, outputStream* errstream);
  int write(const LogDecorations& decorations, const char* msg);
  int write(LogMessageBuffer::Iterator msg_iterator);
  int write_blocking(const LogDecorations& decorations, const char* msg);
  virtual bool flush(int written);
  virtual void describe(outputStream* out);
};

class LogStdoutOutput : public LogFileStreamOutput {
  friend class LogFileStreamInitializer;
 private:
  LogStdoutOutput() : LogFileStreamOutput(stdout) {
    set_config_string("all=warning");
  }
  virtual bool initialize(const char* options, outputStream* errstream) {
    return false;
  }
 public:
  virtual const char* name() const {
    return "stdout";
  }
};

class LogStderrOutput : public LogFileStreamOutput {
  friend class LogFileStreamInitializer;
 private:
  LogStderrOutput() : LogFileStreamOutput(stderr) {
    set_config_string("all=off");
  }
  virtual bool initialize(const char* options, outputStream* errstream) {
    return false;
  }
 public:
  virtual const char* name() const {
    return "stderr";
  }
};


// sempahore-based mutex. Implementation of flockfile do not work with LogFileOuptut::rotate()
// because fclose() automatically unlocks FILE->_lock and nullifies FileLocker protection.
class FileLocker : public StackObj {
  Semaphore& _sem;
  debug_only(static intx _locking_thread_id;)

 public:
  FileLocker(Semaphore& sem) : _sem(sem) {
    sem.wait();
    debug_only(_locking_thread_id = os::current_thread_id());
  }

  ~FileLocker() {
    debug_only(_locking_thread_id = -1);
    _sem.signal();
  }

  debug_only(static bool current_thread_has_lock();)
};

extern LogStderrOutput &StderrLog;
extern LogStdoutOutput &StdoutLog;

#endif // SHARE_LOGGING_LOGFILESTREAMOUTPUT_HPP
