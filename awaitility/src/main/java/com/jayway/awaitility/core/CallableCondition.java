/*
 * Copyright 2010 the original author or authors.
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
package com.jayway.awaitility.core;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import static com.jayway.awaitility.core.LambdaErrorMessageGenerator.generateLambdaErrorMessagePrefix;
import static com.jayway.awaitility.core.LambdaErrorMessageGenerator.isLambdaClass;
import static com.jayway.awaitility.spi.Timeout.timeout_message;

class CallableCondition implements Condition<Void> {

    private final ConditionAwaiter conditionAwaiter;

    private final ConditionEvaluationHandler<Object> conditionEvaluationHandler;

    /**
     * <p>Constructor for CallableCondition.</p>
     *
     * @param matcher  a {@link java.util.concurrent.Callable} object.
     * @param settings a {@link com.jayway.awaitility.core.ConditionSettings} object.
     */
    public CallableCondition(final Callable<Boolean> matcher, ConditionSettings settings) {
        conditionEvaluationHandler = new ConditionEvaluationHandler<Object>(null, settings);
        ConditionEvaluationWrapper conditionEvaluationWrapper = new ConditionEvaluationWrapper(matcher, settings, conditionEvaluationHandler);
        conditionAwaiter = new ConditionAwaiter(conditionEvaluationWrapper, settings) {
            @SuppressWarnings("rawtypes")
            @Override
            protected String getTimeoutMessage() {
                if (timeout_message != null) {
                    return timeout_message;
                }
                final String timeoutMessage;
                if (matcher == null) {
                    timeoutMessage = "";
                } else {
                    final Class<? extends Callable> type = matcher.getClass();
                    final Method enclosingMethod = type.getEnclosingMethod();
                    if (type.isAnonymousClass() && enclosingMethod != null) {
                        timeoutMessage = String.format("Condition returned by method \"%s\" in class %s was not fulfilled",
                                enclosingMethod.getName(), enclosingMethod.getDeclaringClass().getName());
                    } else {
                        final String message;
                        if (isLambdaClass(type)) {
                            message = "with " + generateLambdaErrorMessagePrefix(type, false);
                        } else {
                            message = type.getName();
                        }
                        timeoutMessage = String.format("Condition %s was not fulfilled", message);
                    }
                }
                return timeoutMessage;
            }
        };
    }

    /**
     * <p>await</p>
     *
     * @return a {@link java.lang.Void} object.
     */
    public Void await() {
        conditionEvaluationHandler.start();
        conditionAwaiter.await();
        return null;
    }

    /**
     * Wraps and delegates to another callable and invokes the {@link com.jayway.awaitility.core.ConditionEvaluationHandler}.
     */
    private static class ConditionEvaluationWrapper implements Callable<Boolean> {

        private final Callable<Boolean> matcher;
        private final ConditionSettings settings;
        private final ConditionEvaluationHandler<Object> conditionEvaluationHandler;

        ConditionEvaluationWrapper(Callable<Boolean> matcher, ConditionSettings settings, ConditionEvaluationHandler<Object> conditionEvaluationHandler) {

            this.matcher = matcher;
            this.settings = settings;
            this.conditionEvaluationHandler = conditionEvaluationHandler;
        }

        public Boolean call() throws Exception {
            boolean conditionFulfilled = matcher.call();
            if (conditionFulfilled) {
                conditionEvaluationHandler.handleConditionResultMatch(getMatchMessage(matcher, settings.getAlias()), true);
            } else {
                conditionEvaluationHandler.handleConditionResultMismatch(getMismatchMessage(matcher, settings.getAlias()), false);

            }
            return conditionFulfilled;
        }

        private String getMatchMessage(Callable<Boolean> matcher, String conditionAlias) {
            return generateDescriptionPrefix(matcher, conditionAlias) + " returned true";
        }

        private String getMismatchMessage(Callable<Boolean> matcher, String conditionAlias) {
            return generateDescriptionPrefix(matcher, conditionAlias) + " returned false";
        }

        private String generateDescriptionPrefix(Callable<Boolean> matcher, String conditionAlias) {
            String methodDescription = generateMethodDescription(matcher);
            boolean hasAlias = conditionAlias != null;
            if (isLambdaClass(matcher.getClass())) {
                final String prefix;
                if (hasAlias) {
                    prefix = "Condition with alias " + conditionAlias + " defined as a ";
                } else {
                    prefix = "Condition defined as a ";
                }
                return prefix + generateLambdaErrorMessagePrefix(matcher.getClass(), false) + methodDescription;
            }
            return "Callable condition" + (hasAlias ? " with alias " + conditionAlias : "") + methodDescription;
        }

        private String generateMethodDescription(Callable<Boolean> matcher) {
            String methodDescription = "";
            Method enclosingMethod = matcher.getClass().getEnclosingMethod();
            if (enclosingMethod != null) {
                methodDescription = " defined in " + enclosingMethod.toString();
            }
            return methodDescription;
        }
    }
}
