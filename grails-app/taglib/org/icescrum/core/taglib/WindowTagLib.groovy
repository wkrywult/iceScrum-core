/*
 * Copyright (c) 2014 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.taglib
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes
import org.codehaus.groovy.grails.web.metaclass.ControllerDynamicMethods
import org.codehaus.groovy.grails.web.mapping.ForwardUrlMappingInfo
import org.codehaus.groovy.grails.web.util.WebUtils

class WindowTagLib {
    static namespace = 'is'

    def springSecurityService
    def grailsApplication
    def securityService

    /**
     * Generate a window
     * The attribute "id" is obligatory
     */
    def window = { attrs, body ->
        def windowId = attrs.window ?: controllerName
        attrs.type = attrs.type ?: 'window'
        def includeParams = ['type':attrs.type]
        params.each{ if (!(it.key in ["controller", "action"])) { includeParams << it} }

        // Check for content window
        def windowContent
        if (attrs.init){
            def result = includeContent([controller: windowId, action: attrs.init, params:includeParams])
            if (result.contentType == 'application/json;charset=utf-8'){
                response.setStatus(400)
                response.setContentType(result.contentType)
                out << result.content
                return
            }else{
                windowContent = result.content
            }
        }
        else {
            windowContent = body()
        }

        // Check for toolbar existence
        attrs.toolbar = attrs.toolbar ? include(controller: windowId, action: attrs.type == 'widget' ? 'toolbarWidget' : 'toolbar', params: includeParams) : attrs.toolbar != false

        // Check for right content
        attrs.right = attrs.right ? include(controller: windowId, action: 'right', params: includeParams) : null


        // Check for shortcuts
        if (attrs.shortcuts) {
            attrs.help = attrs.help ?: ""
            attrs.help += "<span class='help-shortcut-title'>${message(code: 'is.ui.shortcut.title')}</span>"
            attrs.shortcuts.each {
                attrs.help += "<p class='keyboard-mappings'>"
                attrs.help += "<span class='code box-simple ui-corner-all'>${message(code: it.code)}</span>"
                attrs.help += "${message(code: it.text)}"
                attrs.help += "</p>"
            }
        }

        def params = [
                spaceName: attrs.spaceName,
                type: attrs.type,
                title: attrs.title ?: null,
                windowActions: attrs.windowActions ?: [
                        help: attrs.help ?: null,
                        widgetable: attrs.widgetable ? true : false,
                        fullScreen: attrs.fullScreen ?: false,
                        printable:attrs.printable
                ],
                id: windowId,
                toolbar: attrs.toolbar,
                resizable: attrs.resizable ?: false,
                sortable: attrs.sortable ?: false,
                right:attrs.right,
                contentClass: attrs.contentClass,
                windowContent: windowContent
        ]
        if (windowContent && !webRequest?.params?.returnError){
            out << g.render(template: '/components/window', plugin: 'icescrum-core', model: params)
        }
    }

    def buttonNavigation = { attrs, body ->
        attrs."class" = attrs."class" ? attrs."class" : ""
        attrs."class" += attrs.button ? attrs.button : " button-n"
        attrs.remove("button");

        def str = "<span class='start'></span><span class='content'>"

        if (attrs.icon) {
            attrs."class" += " button-ico button-" + attrs.icon
            str += "<span class='ico'></span>"
            attrs.remove('icon')
        }

        if (!attrs.text)
            attrs.text = body()?.trim()

        str += "${attrs.text}</span><span class='end'>"
        if (attrs.dropmenu == 'true')
            str += "<span class='arrow'></span>"

        str += "</span>"
        attrs.remove("text");

        out << is.link(attrs, str).trim();

    }

    /**
     * Generate a widget using the is:window tag
     */
    def widget = { attrs, body ->
        attrs = attrs.attrs ?: attrs
        def params = [
                type: 'widget',
                title: attrs.title,
                windowActions: [
                        help: attrs.help ?: null,
                        closeable: attrs.closeable ?: false,
                        windowable: attrs.windowable ?: false
                ],
                window: attrs.id,
                sortable: attrs.sortable,
                resizable: attrs.resizable?:false,
                toolbar: attrs.toolbar,
                init: attrs.init
        ]
        out << is.window(params, {})
    }

    //TODO Remove when no reference left
    def shortcut = {attrs ->
        if (request.readOnly){
            return
        }
        assert attrs.key
        assert attrs.callback

        if (attrs.scope)
            attrs.scope = "keydown.${attrs.scope}"
        else
            attrs.scope = "keydown"
        if (!attrs.listenOn) {
            attrs.listenOn = "document"
        }
        def escapedKey = attrs.key.replace('+', '').replace('.', '')
        def jqCode = "jQuery(${attrs.listenOn}).unbind('${attrs.scope}.${escapedKey}');"
        jqCode += "jQuery(${attrs.listenOn}).bind('${attrs.scope}.${escapedKey}','${attrs.key}',function(e){${attrs.callback}e.preventDefault();});"
        out << jq.jquery(null, jqCode)
    }

    def includeContent(attrs) {
        if (attrs.action && !attrs.controller) {
            def controller = request?.getAttribute(GrailsApplicationAttributes.CONTROLLER)
            def controllerName = controller?.getProperty(ControllerDynamicMethods.CONTROLLER_NAME_PROPERTY)
            attrs.controller = controllerName
        }

        if (attrs.controller || attrs.view) {
            def mapping = new ForwardUrlMappingInfo(controller: attrs.controller,
                                                    action: attrs.action,
                                                    view: attrs.view,
                                                    id: attrs.id,
                                                    params: attrs.params)
            return WebUtils.includeForUrlMappingInfo(request, response, mapping, attrs.model ?: [:])
        }
    }
}