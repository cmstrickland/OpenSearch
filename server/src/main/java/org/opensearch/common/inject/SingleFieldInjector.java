/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Copyright (C) 2008 Google Inc.
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

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.common.inject;

import org.opensearch.common.inject.internal.Errors;
import org.opensearch.common.inject.internal.ErrorsException;
import org.opensearch.common.inject.internal.InternalContext;
import org.opensearch.common.inject.internal.InternalFactory;
import org.opensearch.common.inject.spi.Dependency;
import org.opensearch.common.inject.spi.InjectionPoint;

import java.lang.reflect.Field;

/**
 * Sets an injectable field.
 */
class SingleFieldInjector implements SingleMemberInjector {
    final Field field;
    final InjectionPoint injectionPoint;
    final Dependency<?> dependency;
    final InternalFactory<?> factory;

    SingleFieldInjector(InjectorImpl injector, InjectionPoint injectionPoint, Errors errors)
            throws ErrorsException {
        this.injectionPoint = injectionPoint;
        this.field = (Field) injectionPoint.getMember();
        this.dependency = injectionPoint.getDependencies().get(0);
        factory = injector.getInternalFactory(dependency.getKey(), errors);
    }

    @Override
    public InjectionPoint getInjectionPoint() {
        return injectionPoint;
    }

    @Override
    public void inject(Errors errors, InternalContext context, Object o) {
        errors = errors.withSource(dependency);

        context.setDependency(dependency);
        try {
            Object value = factory.get(errors, context, dependency);
            field.set(o, value);
        } catch (ErrorsException e) {
            errors.withSource(injectionPoint).merge(e.getErrors());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e); // a security manager is blocking us, we're hosed
        } finally {
            context.setDependency(null);
        }
    }
}
