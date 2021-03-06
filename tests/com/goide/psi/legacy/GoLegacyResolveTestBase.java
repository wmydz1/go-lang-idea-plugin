/*
 * Copyright 2013-2014 Sergey Ignatov, Alexander Zolotov, Mihai Toader
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide.psi.legacy;

import com.goide.GoCodeInsightFixtureTestCase;
import com.goide.psi.GoFile;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.FilteringProcessor;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public abstract class GoLegacyResolveTestBase extends GoCodeInsightFixtureTestCase {
  @NotNull public static final String REF_MARK = "/*ref*/";
  @NotNull public static final String DEF_MARK = "/*def*/";

  @Nullable protected PsiReference myReference;
  @Nullable protected PsiElement myDefinition;

  @Override
  protected String getBasePath() {
    return "psi/resolve";
  }

  protected void doResolveTest(boolean lowercase) {
    doResolveTest(getTestName(lowercase) + ".go");
  }
  
  protected void doDirTest() {
    String testDataPath = getTestDataPath();
    File fromFile = new File(testDataPath + "/" + getTestName(false));
    if (fromFile.isDirectory()) {
      VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(fromFile.getPath());
      assert dir != null;
      doDirectoryTest(dir);
    }
  }

  private void doResolveTest(@NotNull String filePath) {
    processPsiFile((GoFile)myFixture.configureByFile(filePath));
    if (myReference == null) fail("no reference defined in test case");
    PsiElement resolve = myReference.resolve();
    if (resolve != null && myDefinition == null && !allowNullDefinition()) fail("element resolved but it shouldn't have");
    if (resolve == null && myDefinition != null) fail("element didn't resolve when it should have");
    if (resolve != null) {
      if (!allowNullDefinition()) {
        PsiElement def = PsiTreeUtil.getParentOfType(myDefinition, resolve.getClass(), false);
        assertSame(def, resolve);
      }
    }
    else {
      processNullResolve();
    }
  }

  protected boolean allowNullDefinition() {
    return false;
  }

  protected void processNullResolve() {
  }

  private void processPsiFile(@NotNull GoFile file) {
    String fileContent = loadText(file.getVirtualFile());
    
    int refIndex = fileContent.indexOf(REF_MARK);
    String fileName = file.getName();
    if (refIndex != -1) {
      if (myReference != null) fail("only once reference should be declared in a test case, see file: " + fileName);
      int offset = refIndex + REF_MARK.length();
      myReference = file.findReferenceAt(offset);
      if (myReference == null) fail("no reference was found as marked in file: " + fileName + ", offset: " + offset);
    }

    int defIndex = fileContent.indexOf(DEF_MARK);
    if (defIndex != -1) {
      if (myDefinition != null) fail("only one definition should be allowed in a resolve test case, see file: " + fileName);
      int offset = defIndex + DEF_MARK.length();
      myDefinition = file.findElementAt(offset);
      if (myDefinition == null) fail("no definition was found where marked in file: " + fileName + ", offset: " + offset);
    }
  }

  private void doDirectoryTest(@NotNull final VirtualFile file) {
    VfsUtilCore.processFilesRecursively(
      file,
      new FilteringProcessor<VirtualFile>(
        new Condition<VirtualFile>() {
          @Override
          public boolean value(VirtualFile virtualFile) {
            return !virtualFile.isDirectory() && virtualFile.getName().endsWith(".go");
          }
        },
        new Processor<VirtualFile>() {
          @Override
          public boolean process(VirtualFile virtualFile) {
            PsiFile goFile = myFixture.getPsiManager().findFile(virtualFile);
            assert goFile instanceof GoFile;
            processPsiFile((GoFile)goFile);
            return true;
          }
        }
      )
    );
  }

  protected void doTest() { doResolveTest(false); }
}
