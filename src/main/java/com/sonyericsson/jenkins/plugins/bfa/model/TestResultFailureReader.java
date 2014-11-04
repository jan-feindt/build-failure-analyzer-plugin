/*
 * The MIT License
 *
 * Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.jenkins.plugins.bfa.model;

import hudson.console.ConsoleNote;
import hudson.model.AbstractBuild;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.tasks.test.TestResult;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;

import com.sonyericsson.jenkins.plugins.bfa.model.indication.FoundIndication;
import com.sonyericsson.jenkins.plugins.bfa.model.indication.TestResultIndication;


/**
 * Reader used to find indications of a failure cause in test results.
 *
 * @author Jan Feindt &lt;jan.feindt@gmail.com&gt;
 */
public class TestResultFailureReader extends FailureReader {

  private static final Logger logger = Logger.getLogger(TestResultFailureReader.class.getName());

  private static final long TIMEOUT_LINE = 1000;
  private static final long TIMEOUT_FILE = 10000;

  /**
   * Standard constructor.
   * @param indication the indication to look for.
   */
  public TestResultFailureReader(TestResultIndication indication) {
    super(indication);
  }

  /**
   * Scans a build test result.
   *
   * @param build - the build whose test result should be scanned.
   * @return a FoundIndication if the pattern given by this TestResultFailureReader
   * is found in the test result of the given build; return null otherwise.
   * @throws IOException if so.
   */
  @Override
  public FoundIndication scan(AbstractBuild build) throws IOException {

    return scanTestResults(build);

  }

  private FoundIndication scanTestResults(AbstractBuild build) {
    TimerThread timerThread = new TimerThread(Thread.currentThread(), TIMEOUT_LINE);
    FoundIndication foundIndication = null;
    boolean found = false;
    final Pattern pattern = indication.getPattern();

    timerThread.start();
    try {
      long startTime = System.currentTimeMillis();
      // scan all failed tests
      List<AbstractTestResultAction> testActions = build.getActions(AbstractTestResultAction.class);

      for (AbstractTestResultAction testAction : testActions) {
        String foundLine = "";
        TestResult foundTestResult = null;
        List<? extends TestResult> failedTests = testAction.getFailedTests();
        try {
          for (TestResult test : failedTests) {
            if (test.getErrorDetails() != null && pattern.matcher(new InterruptibleCharSequence(test.getErrorDetails())).matches()) {
              foundLine = test.getErrorDetails();
              foundTestResult = test;
              found = true;
              break;
            }
            if (test.getErrorStackTrace() != null && pattern.matcher(new InterruptibleCharSequence(test.getErrorStackTrace())).matches()) {
              foundLine = test.getErrorStackTrace();
              foundTestResult = test;
              found = true;
              break;
            }
            if (test.getStderr() != null && pattern.matcher(new InterruptibleCharSequence(test.getStderr())).matches()) {
              foundLine = test.getStderr();
              foundTestResult = test;
              found = true;
              break;
            }
            if (test.getStdout() != null && pattern.matcher(new InterruptibleCharSequence(test.getStdout())).matches()) {
              foundLine = test.getStdout();
              foundTestResult = test;
              found = true;
              break;
            }

          }
        } catch (RuntimeException e) {
          if (e.getCause() instanceof InterruptedException) {
            logger.warning("Timeout scanning for indication '" + indication.toString() + "' for test result "
                + build.getDisplayName());
          } else {
            // This is not a timeout exception
            throw e;
          }
        }
        timerThread.touch();
        if (System.currentTimeMillis() - startTime > TIMEOUT_FILE) {
          logger.warning("File timeout scanning for indication '" + indication.toString() + "' for test result "
              + build.getDisplayName());
          break;
        }

        if (found) {
          String cleanLine = ConsoleNote.removeNotes(foundLine);
          String l_testUrl = Jenkins.getInstance().getRootUrl() + build.getUrl() + "testReport" + foundTestResult.getUrl();
          foundIndication = new FoundIndication(build, pattern.toString(), l_testUrl, cleanLine);
        }
        return foundIndication;
      }
      return foundIndication;
    } finally {
      timerThread.requestStop();
      timerThread.interrupt();
      try {
        timerThread.join();
        //CS IGNORE EmptyBlock FOR NEXT 2 LINES. REASON: unimportant exception
      } catch (InterruptedException eIgnore) {
      }
      // reset the interrupt
      Thread.interrupted();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public FoundIndication scan(AbstractBuild p_build, PrintStream p_buildLog) {
    FoundIndication foundIndication = null;
    long start = System.currentTimeMillis();
    try {
      foundIndication = scanTestResults(p_build);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "[BFA] Could not scan test results for indication: ", e);
      p_buildLog.println("[BFA] Could not scan test results for indication.");
    } finally {

      if (logger.isLoggable(Level.FINER)) {
        logger.log(Level.FINER, "[BFA] [{0}] - [{1}] {2}ms",
            new Object[] { p_build.getFullDisplayName(),
                indication.toString(),
                String.valueOf(System.currentTimeMillis() - start), });
      }
    }
    return foundIndication;
  }


}
