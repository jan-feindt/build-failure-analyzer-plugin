/*
 * The MIT License
 *
 * Copyright 2013 Sony Mobile Communications AB. All rights reserved.
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
package com.sonyericsson.jenkins.plugins.bfa.sod;

import com.sonyericsson.jenkins.plugins.bfa.model.FailureCauseBuildAction;
import com.sonyericsson.jenkins.plugins.bfa.model.FailureCauseMatrixBuildAction;
import com.sonyericsson.jenkins.plugins.bfa.BuildFailureScanner;
import com.sonyericsson.jenkins.plugins.bfa.PluginImpl;

import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.Result;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.output.NullOutputStream;

/**
 * Runnable class for scanning non scanned build.
 *
 * @author Shemeer Sulaiman &lt;shemeer.x.sulaiman@sonymobile.com&gt;
 */
public class ScanOnDemandTask implements Runnable {

  private static final Logger logger = Logger.getLogger(ScanOnDemandTask.class.getName());
  private AbstractBuild build;

  /**
   * SODExecutor constructor.
   *
   * @param build the build to analyze.
   */
  public ScanOnDemandTask(final AbstractBuild build) {
    this.build = build;
  }

  @Override
  public void run() {
    try {
      if (build instanceof MatrixBuild) {
        List<MatrixRun> runs = ((MatrixBuild)build).getRuns();
        for (AbstractBuild run : runs) {
          if (run.getActions(FailureCauseBuildAction.class).isEmpty()
              && run.getActions(FailureCauseMatrixBuildAction.class).isEmpty()
              && run.getResult().isWorseThan(Result.SUCCESS)) {
            if (run.getNumber() == build.getNumber()) {
              scanBuild(run);
            }
          }
        }
        endMatrixBuildScan();
      } else {
        scanBuild(build);
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to add a FailureScanner to "
          + build.getProject().getFullDisplayName(), e);
    }
  }

  /**
   * Method will add matrix sub job
   * failure causes to parent job.
   * @throws IOException IOException
   */
  public void endMatrixBuildScan() throws IOException {
    List<MatrixRun> runs = ((MatrixBuild)build).getRuns();
    List<MatrixRun> runsWithCorrectNumber = new LinkedList<MatrixRun>();
    int i = 0;
    for (MatrixRun run : runs) {
      if (run.getNumber() == build.getNumber()) {
        runsWithCorrectNumber.add(run);
      }
    }
    build.addAction(new FailureCauseMatrixBuildAction((MatrixBuild)build, runsWithCorrectNumber));
    build.save();
  }

  /**
   * Scan the non scanned old build.
   *
   * @param abstractBuild the non-scanned/scanned build to scan/rescan.
   */
  public void scanBuild(AbstractBuild abstractBuild) {
    OutputStream fos = null;
    try {
      // don't change build log in rescan mode, use /dev/null
      fos = new NullOutputStream();
      PrintStream buildLog = new PrintStream(fos, true, "UTF8");
      PluginImpl.getInstance().getKnowledgeBase().removeBuildfailurecause(abstractBuild);
      BuildFailureScanner.scanIfNotScanned(abstractBuild, buildLog);
      abstractBuild.save();
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Could not get the causes from the knowledge base", e);
    } finally {
      if (fos != null) {
        try {
          fos.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, "Failed to close the build log file " + abstractBuild.getLogFile(), e);
        }
      }
    }
  }

}
