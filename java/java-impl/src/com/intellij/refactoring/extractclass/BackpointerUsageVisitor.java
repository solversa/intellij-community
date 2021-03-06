/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.refactoring.extractclass;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class BackpointerUsageVisitor extends JavaRecursiveElementWalkingVisitor {
  private PsiMember myCause;

  private final List<? extends PsiField> myFields;
  private final List<? extends PsiClass> myInnerClasses;
  private final List<? extends PsiMethod> myMethods;
  private final PsiClass mySourceClass;
  private final boolean myCheckThisExpression;


  BackpointerUsageVisitor(final List<? extends PsiField> fields,
                                 final List<? extends PsiClass> innerClasses, final List<? extends PsiMethod> methods, final PsiClass sourceClass) {
    this(fields, innerClasses, methods, sourceClass, true);
  }

  BackpointerUsageVisitor(List<? extends PsiField> fields, List<? extends PsiClass> innerClasses, List<? extends PsiMethod> methods, PsiClass sourceClass,
                                 final boolean checkThisExpression) {
    myFields = fields;
    myInnerClasses = innerClasses;
    myMethods = methods;
    mySourceClass = sourceClass;
    myCheckThisExpression = checkThisExpression;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (myCause != null) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitReferenceExpression(PsiReferenceExpression expression) {
    if (myCause != null) {
      return;
    }
    super.visitReferenceExpression(expression);
    final PsiExpression qualifier = expression.getQualifierExpression();

    final PsiElement referent = expression.resolve();
    if (!(referent instanceof PsiField)) {
      return;
    }
    final PsiField field = (PsiField)referent;
    if (myFields.contains(field) || myInnerClasses.contains(field.getContainingClass())) {
      return;
    }
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      return;
    }
    if (qualifier == null || (myCheckThisExpression && (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression))) {
      myCause = field;
    }
  }

  @Override
  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    if (myCause != null) {
      return;
    }
    super.visitMethodCallExpression(expression);
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (myMethods.contains(method) || myInnerClasses.contains(containingClass)) {
      return;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return;
    }
    if (!containingClass.equals(mySourceClass)) {
      return;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null || (myCheckThisExpression && (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression))) {
      myCause = method;
    }
  }

  public boolean isBackpointerRequired() {
    return myCause != null;
  }

  public PsiMember getCause() {
    return myCause;
  }

  public boolean backpointerRequired() {
    for (PsiMethod method : myMethods) {
      method.accept(this);
      if (myCause != null) {
        return true;
      }
    }
    for (PsiField field : myFields) {
      field.accept(this);
      if (myCause != null) {
        return true;
      }
    }
    for (PsiClass innerClass : myInnerClasses) {
      innerClass.accept(this);
      if (myCause != null) {
        return true;
      }
    }
    return false;
  }

}