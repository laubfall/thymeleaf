/*
 * =============================================================================
 *
 *   Copyright (c) 2011-2014, The THYMELEAF team (http://www.thymeleaf.org)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 * =============================================================================
 */
package org.thymeleaf.engine.markup.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.thymeleaf.util.StringUtils;

/**
 *
 * @author Daniel Fern&aacute;ndez
 * @since 3.0.0
 *
 */
final class MarkupSelectorItem {

    private static final String TEXT_SELECTOR = "text()";
    private static final String ID_MODIFIER_SEPARATOR = "#";
    private static final String CLASS_MODIFIER_SEPARATOR = ".";
    private static final String REFERENCE_MODIFIER_SEPARATOR = "%";

    private static final String ID_ATTRIBUTE_NAME = "id";
    private static final String CLASS_ATTRIBUTE_NAME = "class";

    private static final String ODD_SELECTOR = "odd()";
    private static final String EVEN_SELECTOR = "even()";
    static final Integer INDEX_ODD = Integer.valueOf(Integer.MAX_VALUE - 1);
    static final Integer INDEX_EVEN = Integer.valueOf(Integer.MAX_VALUE - 2);

    private static final String selectorPatternStr = "^(/{1,2})([^/\\s]*?)(\\[(?:.*)\\])?$";
    private static final Pattern selectorPattern = Pattern.compile(selectorPatternStr);
    private static final String modifiersPatternStr = "^(?:\\[(.*?)\\])(\\[(?:.*)\\])?$";
    private static final Pattern modifiersPattern = Pattern.compile(modifiersPatternStr);


    final boolean caseSensitive;
    final boolean anyLevel;
    final boolean textSelector;
    final String elementName;
    final String referenceName;
    final IndexCondition index;
    final List<AttributeCondition> attributeConditions;
    final boolean requiresAttributesInElement;


    // TODO Add to github issues a ticket explaining that the syntax is now extended to:
    //                * support attribute "existence" and "non-existence"
    //                * support indexes "even()" and "odd()"
    //                * support "contains" attribute value with "*="
    //                * support for > and < in indexed selectors

    MarkupSelectorItem(
            final boolean caseSensitive, final boolean anyLevel, final boolean textSelector, final String elementName,
            final String referenceName, final IndexCondition index, final List<AttributeCondition> attributeConditions) {

        super();

        this.caseSensitive = caseSensitive;
        this.anyLevel = anyLevel;
        this.textSelector = textSelector;
        this.elementName = elementName;
        this.referenceName = referenceName;
        this.index = index;
        this.attributeConditions = Collections.unmodifiableList(attributeConditions);

        // This is used in order to perform quick checks when matching: if this selector requires the existence
        // of at least one attribute in the element and the element has none, we know it won't match.
        boolean newRequiresAttributesInElement = false;
        for (final AttributeCondition attributeCondition : this.attributeConditions) {
            if (!attributeCondition.getOperator().equals(AttributeCondition.Operator.NOT_EQUALS) &&
                !attributeCondition.getOperator().equals(AttributeCondition.Operator.NOT_EXISTS)) {
                newRequiresAttributesInElement = true;
            }
        }
        this.requiresAttributesInElement = newRequiresAttributesInElement;

    }



    static List<MarkupSelectorItem> parseSelector(final boolean caseSensitive, final String selector) {
        return Collections.unmodifiableList(parseSelector(caseSensitive, selector, true));
    }




    private static List<MarkupSelectorItem> parseSelector(final boolean caseSensitive, final String selector, final boolean atRootLevel) {

        /*
         * STRATEGY: We will divide the Selector into several, one for each level, and chain them all using the
         * 'next' property. That way, a '/x//y[0]/z[@id='a']' selector will be divided into three chained selectors,
         * like: '/x' -(next)-> '//y[0]' -(next)-> '/z[@id='a']'
         */

        String selectorSpecStr = selector.trim();
        if (atRootLevel) {
            if (!selectorSpecStr.startsWith("/")) {
                // "x" is equivalent to "//x"
                selectorSpecStr = "//" + selectorSpecStr;
            }
        } // if we are not at root level, expression will always start with "/", and that's fine.

        final int selectorSpecStrLen = selectorSpecStr.length();
        int firstNonSlash = 0;
        while (firstNonSlash < selectorSpecStrLen && selectorSpecStr.charAt(firstNonSlash) == '/') {
            firstNonSlash++;
        }

        if (firstNonSlash >= selectorSpecStrLen) {
            throw new IllegalArgumentException(
                    "Invalid syntax in selector \"" + selector + "\": '/' should be followed by " +
                    "further selector specification");
        }

        final List<MarkupSelectorItem> result;
        final int selEnd = selectorSpecStr.substring(firstNonSlash).indexOf('/');
        if (selEnd != -1) {
            final String tail = selectorSpecStr.substring(firstNonSlash).substring(selEnd);
            selectorSpecStr = selectorSpecStr.substring(0, firstNonSlash + selEnd);
            result = parseSelector(caseSensitive, tail, false);
        } else {
            result = new ArrayList(3);
        }

        final Matcher matcher = selectorPattern.matcher(selectorSpecStr);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid syntax in selector \"" + selector + "\": selector does not match selector syntax: " +
                    "((/|//)?selector)?([@attrib=\"value\" (and @attrib2=\"value\")?])?([index])?");
        }

        final String rootGroup = matcher.group(1);
        final String selectorNameGroup = matcher.group(2);
        final String modifiersGroup = matcher.group(3);

        if (rootGroup == null) {
            throw new IllegalArgumentException(
                    "Invalid syntax in selector \"" + selector + "\": selector does not match selector syntax: " +
                    "((/|//)?selector)?([@attrib=\"value\" (and @attrib2=\"value\")?])?([index])?");
        }

        final boolean anyLevel;
        if ("//".equals(rootGroup)) {
            anyLevel = true;
        } else if ("/".equals(rootGroup)) {
            anyLevel = false;
        } else {
            throw new IllegalArgumentException(
                    "Invalid syntax in selector \"" + selector + "\": selector does not match selector syntax: " +
                    "((/|//)?selector)?([@attrib=\"value\" (and @attrib2=\"value\")?])?([index])?");
        }

        if (selectorNameGroup == null) {
            throw new IllegalArgumentException(
                    "Invalid syntax in selector \"" + selector + "\": selector does not match selector syntax: " +
                    "((/|//)?selector)?([@attrib=\"value\" (and @attrib2=\"value\")?])?([index])?");
        }


        /*
         * ----------------------------------------------------------
         * Process path: extract id, class, reference modifiers...
         * ----------------------------------------------------------
         */

        String path = selectorNameGroup;


        IndexCondition index = null;
        final List<AttributeCondition> attributes = new ArrayList<AttributeCondition>(2);


        final int idModifierPos = path.indexOf(ID_MODIFIER_SEPARATOR);
        final int classModifierPos = path.indexOf(CLASS_MODIFIER_SEPARATOR);
        final int referenceModifierPos = path.indexOf(REFERENCE_MODIFIER_SEPARATOR);


        /*
         * Compute the possible existence of an ID selector: "x#id"
         */

        if (idModifierPos != -1) {
            if (classModifierPos != -1 || referenceModifierPos != -1) {
                throw new IllegalArgumentException(
                        "More than one modifier (id, class, reference) have been specified at " +
                        "selector expression \"" + selector + "\", which is forbidden.");
            }
            final String selectorPathIdModifier = path.substring(idModifierPos + ID_MODIFIER_SEPARATOR.length());
            path = path.substring(0, idModifierPos);
            if (StringUtils.isEmptyOrWhitespace(selectorPathIdModifier)) {
                throw new IllegalArgumentException(
                        "Empty id modifier in selector expression " +
                        "\"" + selector + "\", which is forbidden.");
            }
            attributes.add(new AttributeCondition(ID_ATTRIBUTE_NAME, AttributeCondition.Operator.EQUALS, selectorPathIdModifier));
        }

        /*
         * Compute the possible existence of a CLASS selector: "x.class"
         */

        if (classModifierPos != -1) {
            if (idModifierPos != -1 || referenceModifierPos != -1) {
                throw new IllegalArgumentException(
                        "More than one modifier (id, class, reference) have been specified at " +
                        "selector expression \"" + selector + "\", which is forbidden.");
            }
            final String selectorPathClassModifier = path.substring(classModifierPos + CLASS_MODIFIER_SEPARATOR.length());
            path = path.substring(0, classModifierPos);
            if (StringUtils.isEmptyOrWhitespace(selectorPathClassModifier)) {
                throw new IllegalArgumentException(
                        "Empty id modifier in selector expression " +
                        "\"" + selector + "\", which is forbidden.");
            }
            attributes.add(new AttributeCondition(CLASS_ATTRIBUTE_NAME, AttributeCondition.Operator.EQUALS, selectorPathClassModifier));
        }

        /*
         * Compute the possible existence of a REFERENCE selector: "x%ref"
         */

        String selectorPathReferenceModifier = null;
        if (referenceModifierPos != -1) {
            if (idModifierPos != -1 || classModifierPos != -1) {
                throw new IllegalArgumentException(
                        "More than one modifier (id, class, reference) have been specified at " +
                        "selector expression \"" + selector + "\", which is forbidden.");
            }
            selectorPathReferenceModifier = path.substring(referenceModifierPos + REFERENCE_MODIFIER_SEPARATOR.length());
            path = path.substring(0, referenceModifierPos);
            if (StringUtils.isEmptyOrWhitespace(selectorPathReferenceModifier)) {
                throw new IllegalArgumentException(
                        "Empty id modifier in selector expression " +
                        "\"" + selector + "\", which is forbidden.");
            }
        }


        /*
         * Compute the possibility that our path selector is a TEXT selector: "text()"
         */

        final boolean textSelector = TEXT_SELECTOR.equals(path);


        /*
         * Compute the final path selector we're left with (if any)
         */

        final String selectorPath =
                (textSelector ?
                        null :
                        (StringUtils.isEmptyOrWhitespace(path) ?
                                null :
                                (caseSensitive ?
                                        path :
                                        path.toLowerCase())));


        /*
         * Process classifiers: attributes and index.
         */

        if (modifiersGroup != null) {

            /*
             * A selector level can include two types of filters between [...], in this order:
             *   * 1. Attribute based: [@a='X' and @b='Y'], any number of them: [@a='X'][@b='Y']...
             *   * 2. Index based: [23]
             */

            String remainingModifiers = modifiersGroup;

            while (remainingModifiers != null) {

                // This pattern is made to be recursive, acting group 2 as the recursion tail
                final Matcher modifiersMatcher = modifiersPattern.matcher(remainingModifiers);
                if (!modifiersMatcher.matches()) {
                    throw new IllegalArgumentException(
                            "Invalid syntax in selector \"" + selector + "\": selector does not match selector syntax: " +
                            "((/|//)?selector)?([@attrib=\"value\" (and @attrib2=\"value\")?])?([index])?");
                }

                final String currentModifier = modifiersMatcher.group(1);
                remainingModifiers = modifiersMatcher.group(2);

                index = parseIndex(currentModifier);

                if (index != null) {

                    if (remainingModifiers != null) {
                        // If this is an index, it must be the last modifier!
                        throw new IllegalArgumentException(
                                "Invalid syntax in selector \"" + selector + "\": selector does not match selector syntax: " +
                                "((/|//)?selector)?([@attrib=\"value\" (and @attrib2=\"value\")?])?([index])?");
                    }

                } else {
                    // Modifier is not an index

                    final List<AttributeCondition> attribs = parseAttributes(caseSensitive, selector, currentModifier);
                    if (attribs == null) {
                        throw new IllegalArgumentException(
                                "Invalid syntax in selector \"" + selector + "\": selector does not match selector syntax: " +
                                "(/|//)(selector)([@attrib=\"value\" (and @attrib2=\"value\")?])?([index])?");
                    }

                    attributes.addAll(attribs);

                }

            }

        }


        result.add(0, new MarkupSelectorItem(caseSensitive, anyLevel, textSelector, selectorPath, selectorPathReferenceModifier, index, attributes));

        return result;

    }



    private static IndexCondition parseIndex(final String indexGroup) {

        // Look for the 'even()' and 'odd()' selectors
        if (ODD_SELECTOR.equals(indexGroup.toLowerCase())) {
            return IndexCondition.INDEX_CONDITION_ODD;
        }
        if (EVEN_SELECTOR.equals(indexGroup.toLowerCase())) {
            return IndexCondition.INDEX_CONDITION_EVEN;
        }

        if (indexGroup.charAt(0) == '>') {

            try {
                return new IndexCondition(IndexCondition.IndexConditionType.MORE_THAN, Integer.valueOf(indexGroup.substring(1).trim()));
            } catch (final Exception ignored) {
                return null;
            }

        } else if (indexGroup.charAt(0) == '<') {

            try {
                return new IndexCondition(IndexCondition.IndexConditionType.LESS_THAN, Integer.valueOf(indexGroup.substring(1).trim()));
            } catch (final Exception ignored) {
                return null;
            }

        }

        try {
            return new IndexCondition(IndexCondition.IndexConditionType.VALUE, Integer.valueOf(indexGroup.trim()));
        } catch (final Exception ignored) {
            return null;
        }

    }



    private static List<AttributeCondition> parseAttributes(final boolean caseSensitive, final String selectorSpec, final String indexGroup) {
        final List<AttributeCondition> attributes = new ArrayList<AttributeCondition>(3);
        parseAttributes(caseSensitive, selectorSpec, attributes, indexGroup);
        return attributes;
    }


    private static void parseAttributes(final boolean caseSensitive, final String selectorSpec, final List<AttributeCondition> attributes, final String indexGroup) {

        String att = null;
        final int andPos = indexGroup.indexOf(" and ");
        if (andPos != -1) {
            att = indexGroup.substring(0,andPos);
            final String tail = indexGroup.substring(andPos + 5);
            parseAttributes(caseSensitive, selectorSpec, attributes, tail);
        } else {
            att = indexGroup;
        }

        parseAttribute(caseSensitive, selectorSpec, attributes, att.trim());

    }


    private static void parseAttribute(final boolean caseSensitive, final String selectorSpec, final List<AttributeCondition> attributes, final String attributeSpec) {

        // 0 = attribute name, 1 = operator, 2 = value
        final String[] fragments = AttributeCondition.Operator.extractOperator(attributeSpec);

        String attrName = fragments[0];
        if (attrName.startsWith("@")) {
            attrName = attrName.substring(1);
        }
        attrName = (caseSensitive? attrName : attrName.toLowerCase());

        final AttributeCondition.Operator operator = AttributeCondition.Operator.parse(fragments[1]);

        final String attrValue = fragments[2];
        if (attrValue != null) {
            if (!(attrValue.startsWith("\"") && attrValue.endsWith("\"")) && !(attrValue.startsWith("'") && attrValue.endsWith("'"))) {
                throw new IllegalArgumentException(
                        "Invalid syntax in selector: \"" + selectorSpec + "\"");
            }
            attributes.add(0, new AttributeCondition(attrName, operator, attrValue.substring(1, attrValue.length() - 1)));
        } else {
            attributes.add(0, new AttributeCondition(attrName, operator, null));
        }

    }





    static final class AttributeCondition {

        static enum Operator {
            EQUALS("="), NOT_EQUALS("!="), STARTS_WITH("^="), ENDS_WITH("$="), EXISTS("*"), NOT_EXISTS("!"), CONTAINS("*=");

            private String stringRepresentation;

            Operator(final String stringRepresentation) {
                this.stringRepresentation = stringRepresentation;
            }

            static Operator parse(final String operatorStr) {
                if (operatorStr == null) {
                    return null;
                }
                if ("=".equals(operatorStr)) {
                    return EQUALS;
                }
                if ("!=".equals(operatorStr)) {
                    return NOT_EQUALS;
                }
                if ("^=".equals(operatorStr)) {
                    return STARTS_WITH;
                }
                if ("$=".equals(operatorStr)) {
                    return ENDS_WITH;
                }
                if ("*=".equals(operatorStr)) {
                    return CONTAINS;
                }
                if ("!".equals(operatorStr)) {
                    return NOT_EXISTS;
                }
                if ("".equals(operatorStr)) {
                    return EXISTS;
                }
                return null;
            }

            static String[] extractOperator(final String specification) {
                final int equalsPos = specification.indexOf('=');
                if (equalsPos == -1) {
                    if (specification.charAt(0) == '!') {
                        return new String[] {specification.substring(1).trim(), "!", null};
                    }
                    return new String[] {specification.trim(), "", null};
                }
                final char cprev = specification.charAt(equalsPos - 1);
                switch (cprev) {
                    case '!':
                        return new String[] {
                                specification.substring(0, equalsPos - 1).trim(), "!=",
                                specification.substring(equalsPos + 1).trim()};
                    case '^':
                        return new String[] {
                                specification.substring(0, equalsPos - 1).trim(), "^=",
                                specification.substring(equalsPos + 1).trim()};
                    case '$':
                        return new String[] {
                                specification.substring(0, equalsPos - 1).trim(), "$=",
                                specification.substring(equalsPos + 1).trim()};
                    case '*':
                        return new String[] {
                                specification.substring(0, equalsPos - 1).trim(), "*=",
                                specification.substring(equalsPos + 1).trim()};
                    default:
                        return new String[] {
                                specification.substring(0, equalsPos).trim(), "=",
                                specification.substring(equalsPos + 1).trim()};
                }
            }

            public String toString() {
                return stringRepresentation;
            }

        }


        private final String name;
        private final Operator operator;
        private final String value;

        AttributeCondition(final String name, final Operator operator, final String value) {
            super();
            this.name = name;
            this.operator = operator;
            this.value = value;
        }

        String getName() {
            return this.name;
        }

        Operator getOperator() {
            return this.operator;
        }

        String getValue() {
            return this.value;
        }

    }





    public String toString() {

        final StringBuilder strBuilder = new StringBuilder();

        if (this.anyLevel) {
            strBuilder.append("//");
        } else {
            strBuilder.append("/");
        }

        if (this.elementName != null) {
            strBuilder.append(this.elementName);
        } else if (this.textSelector) {
            strBuilder.append(TEXT_SELECTOR);
        } else {
            strBuilder.append("*");
        }

        if (this.referenceName != null) {
            strBuilder.append("[$ref$=");
            strBuilder.append("'" + this.referenceName + "'");
            strBuilder.append("]");
        }

        if (this.attributeConditions != null && !this.attributeConditions.isEmpty()) {
            strBuilder.append("[");
            strBuilder.append(this.attributeConditions.get(0).getName());
            strBuilder.append(this.attributeConditions.get(0).getOperator());
            if (this.attributeConditions.get(0).getValue() != null) {
                strBuilder.append("'" + this.attributeConditions.get(0).getValue() + "'");
            }
            for (int i = 1; i < this.attributeConditions.size(); i++) {
                strBuilder.append(" and ");
                strBuilder.append(this.attributeConditions.get(i).getName());
                strBuilder.append(this.attributeConditions.get(i).getOperator());
                if (this.attributeConditions.get(i).getValue() != null) {
                    strBuilder.append("'" + this.attributeConditions.get(i).getValue() + "'");
                }
            }
            strBuilder.append("]");
        }

        if (this.index != null) {
            strBuilder.append("[");
            switch (this.index.type) {
                case VALUE:
                    strBuilder.append(this.index.value);
                    break;
                case LESS_THAN:
                    strBuilder.append("<" + this.index.value);
                    break;
                case MORE_THAN:
                    strBuilder.append(">" + this.index.value);
                    break;
                case EVEN:
                    strBuilder.append(EVEN_SELECTOR);
                    break;
                case ODD:
                    strBuilder.append(ODD_SELECTOR);
                    break;
            }
            strBuilder.append("]");
        }

        return strBuilder.toString();

    }


    static final class IndexCondition {

        static enum IndexConditionType { VALUE, LESS_THAN, MORE_THAN, EVEN, ODD }
        static IndexCondition INDEX_CONDITION_ODD = new IndexCondition(IndexConditionType.ODD, -1);
        static IndexCondition INDEX_CONDITION_EVEN = new IndexCondition(IndexConditionType.EVEN, -1);

        final IndexConditionType type;
        final int value;

        IndexCondition(final IndexConditionType type, final int value) {
            super();
            this.type = type;
            this.value = value;
        }

    }

}