/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

define(['require', 'utils/Globals', 'pnotify', 'utils/Messages', 'pnotify.buttons', 'pnotify.confirm'], function(require, Globals, pnotify, Messages) {
    'use strict';

    var Utils = {};
    var prevNetworkErrorTime = 0;

    Utils.escapeHtml = function(string) {
        var entityMap = {
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            '"': '&quot;',
            "'": '&#39;',
            "/": '&#x2F;'
        };
        return String(string).replace(/[&<>"'\/]/g, function(s) {
            return entityMap[s];
        });
    }
    Utils.generateUUID = function() {
        var d = new Date().getTime();
        if (window.performance && typeof window.performance.now === "function") {
            d += performance.now(); //use high-precision timer if available
        }
        var uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
            var r = (d + Math.random() * 16) % 16 | 0;
            d = Math.floor(d / 16);
            return (c == 'x' ? r : (r & 0x3 | 0x8)).toString(16);
        });
        return uuid;
    };

    var notify = function(options) {
        return new pnotify(_.extend({
            icon: true,
            hide: true,
            delay: 3000,
            remove: true,
            buttons: {
                classes: {
                    closer: 'fa fa-times',
                    pin_up: 'fa fa-pause',
                    pin_down: 'fa fa-play'
                }
            }
        }, options));
    }
    Utils.notifyInfo = function(options) {
        notify({
            type: "info",
            text: (options.html ? options.content : _.escape(options.content)) || "Info message."
        });
    };

    Utils.notifyWarn = function(options) {
        notify({
            type: "notice",
            text: (options.html ? options.content : _.escape(options.content)) || "Info message."
        });
    };

    Utils.notifyError = function(options) {
        notify({
            type: "error",
            text: (options.html ? options.content : _.escape(options.content)) || "Error occurred."
        });
    };

    Utils.notifySuccess = function(options) {
        notify({
            type: "success",
            text: (options.html ? options.content : _.escape(options.content)) || "Error occurred."
        });
    };

    Utils.notifyConfirm = function(options) {
        var modal = {};
        if (options && options.modal) {
            var myStack = { "dir1": "down", "dir2": "right", "push": "top", 'modal': true };
            modal['addclass'] = 'stack-modal';
            modal['stack'] = myStack;
        }
        notify(_.extend({
            title: 'Confirmation',
            hide: false,
            confirm: {
                confirm: true
            },
            buttons: {
                closer: false,
                sticker: false
            },
            history: {
                history: false
            }

        }, modal, options)).get().on('pnotify.confirm', function() {
            if (options.ok) {
                options.ok();
            }
        }).on('pnotify.cancel', function() {
            if (options.cancel) {
                options.cancel();
            }
        });
    }
    Utils.defaultErrorHandler = function(model, error) {
        if (error && error.status) {
            if (error.status == 401) {
                window.location = 'login.jsp'
            } else if (error.status == 419) {
                window.location = 'login.jsp'
            } else if (error.status == 403) {
                var message = "You are not authorized";
                if (error.statusText) {
                    try {
                        message = JSON.parse(error.statusText).AuthorizationError;
                    } catch (err) {}
                    Utils.notifyError({
                        content: message
                    });
                }
            } else if (error.status == "0" && error.statusText != "abort") {
                var diffTime = (new Date().getTime() - prevNetworkErrorTime);
                if (diffTime > 3000) {
                    prevNetworkErrorTime = new Date().getTime();
                    Utils.notifyError({
                        content: "Network Connection Failure : " +
                            "It seems you are not connected to the internet. Please check your internet connection and try again"
                    });
                }
            } else {
                Utils.serverErrorHandler(model, error)
            }
        } else {
            Utils.serverErrorHandler(model, error)
        }
    };
    Utils.serverErrorHandler = function(model, response) {
        var responseJSON = response ? response.responseJSON : response;
        if (response && responseJSON && (responseJSON.errorMessage || responseJSON.message || responseJSON.error)) {
            Utils.notifyError({
                content: responseJSON.errorMessage || responseJSON.message || responseJSON.error
            });
        } else {
            Utils.notifyError({
                content: Messages.defaultErrorMessage
            });
        }
    };
    Utils.cookie = {
        setValue: function(cname, cvalue) {
            document.cookie = cname + "=" + cvalue + "; ";
        },
        getValue: function(findString) {
            var search = findString + "=";
            var ca = document.cookie.split(';');
            for (var i = 0; i < ca.length; i++) {
                var c = ca[i];
                while (c.charAt(0) == ' ') c = c.substring(1);
                if (c.indexOf(name) == 0) {
                    return c.substring(name.length, c.length);
                }
            }
            return "";
        }
    };
    Utils.localStorage = function() {
        this.setValue = function() {
            localStorage.setItem(arguments[0], arguments[1]);
        }
        this.getValue = function(key, value) {
            var keyValue = localStorage.getItem(key);
            if ((!keyValue || keyValue == "undefined") && (value != undefined)) {
                return this.setLocalStorage(key, value);
            } else {
                if (keyValue === "" || keyValue === "undefined" || keyValue === "null") {
                    return null;
                } else {
                    return keyValue;
                }

            }
        }
        this.removeValue = function() {
            localStorage.removeItem(arguments[0]);
        }
        if (typeof(Storage) === "undefined") {
            _.extend(this, Utils.cookie);
            console.log('Sorry! No Web Storage support');
        }
    }
    Utils.localStorage = new Utils.localStorage();

    Utils.setUrl = function(options) {
        if (options) {
            if (options.mergeBrowserUrl) {
                var param = Utils.getUrlState.getQueryParams();
                if (param) {
                    options.urlParams = $.extend(param, options.urlParams);
                }
            }
            if (options.urlParams) {
                var urlParams = "?";
                _.each(options.urlParams, function(value, key, obj) {
                    if (value) {
                        value = encodeURIComponent(String(value));
                        urlParams += key + "=" + value + "&";
                    }
                });
                urlParams = urlParams.slice(0, -1);
                options.url += urlParams;

            }
            if (options.updateTabState) {
                $.extend(Globals.saveApplicationState.tabState, options.updateTabState());
            }
            Backbone.history.navigate(options.url, { trigger: options.trigger != undefined ? options.trigger : true });
        }
    };

    Utils.getUrlState = {
        getQueryUrl: function(url) {
            var hashValue = window.location.hash;
            if (url) {
                hashValue = url;
            }
            return {
                firstValue: hashValue.split('/')[1],
                hash: hashValue,
                queyParams: hashValue.split("?"),
                lastValue: hashValue.split('/')[hashValue.split('/').length - 1]
            }
        },
        isInitial: function() {
            return this.getQueryUrl().firstValue == undefined ? true : false;
        },
        isTagTab: function() {
            return this.getQueryUrl().firstValue == "tag" ? true : false;
        },
        isTaxonomyTab: function() {
            return this.getQueryUrl().firstValue == "taxonomy" ? true : false;
        },
        isSearchTab: function() {
            return this.getQueryUrl().firstValue == "search" ? true : false;
        },
        isDetailPage: function() {
            return this.getQueryUrl().firstValue == "detailPage" ? true : false;
        },
        getLastValue: function() {
            return this.getQueryUrl().lastValue;
        },
        getFirstValue: function() {
            return this.getQueryUrl().firstValue;
        },
        getQueryParams: function(url) {
            var qs = this.getQueryUrl(url).queyParams[1];
            if (typeof qs == "string") {
                qs = qs.split('+').join(' ');
                var params = {},
                    tokens,
                    re = /[?&]?([^=]+)=([^&]*)/g;
                while (tokens = re.exec(qs)) {
                    params[decodeURIComponent(tokens[1])] = decodeURIComponent(tokens[2]);
                }
                return params;
            }
        },
        getKeyValue: function(key) {
            var paramsObj = this.getQueryParams();
            if (key.length) {
                var values = [];
                _.each(key, function(objKey) {
                    var obj = {};
                    obj[objKey] = paramsObj[objKey]
                    values.push(obj);
                    return values;
                })
            } else {
                return paramsObj[key];
            }
        }
    }
    Utils.checkTagOrTerm = function(value, isTermView) {
        if (value && _.isString(value) && isTermView) {
            // For string break
            if (value == "TaxonomyTerm") {
                return {}
            }
            var name = _.escape(value).split('.');
            return {
                term: true,
                tag: false,
                name: name[name.length - 1],
                fullName: value
            }
        }
        if (value && _.isString(value)) {
            value = {
                typeName: value
            }
        }
        if (_.isObject(value)) {
            var name = "";
            if (value && value.$typeName$) {
                name = value.$typeName$;
            } else if (value && value.typeName) {
                name = value.typeName;
            }
            if (name === "TaxonomyTerm") {
                return {}
            }
            name = _.escape(name).split('.');

            var trem = false;
            if (value['taxonomy.namespace']) {
                trem = true;
            } else if (value.values && value.values['taxonomy.namespace']) {
                trem = true;
            } else if (name.length > 1) {
                trem = true; // Temp fix
            }

            if (trem) {
                return {
                    term: true,
                    tag: false,
                    name: name[name.length - 1],
                    fullName: name.join('.')
                }
            } else {
                return {
                    term: false,
                    tag: true,
                    name: name[name.length - 1],
                    fullName: name.join('.')
                }
            }
        }
    }
    Utils.getName = function() {
        return Utils.extractKeyValueFromEntity.apply(this, arguments).name;
    }
    Utils.getNameWithProperties = function() {
        return Utils.extractKeyValueFromEntity.apply(this, arguments);
    }
    Utils.extractKeyValueFromEntity = function() {
        var collectionJSON = arguments[0],
            priorityAttribute = arguments[1];
        var returnObj = {
            name: '-',
            found: true,
            key: null
        }
        if (collectionJSON) {
            if (collectionJSON.attributes && collectionJSON.attributes[priorityAttribute]) {
                returnObj.name = _.escape(collectionJSON.attributes[priorityAttribute]);
                returnObj.key = priorityAttribute;
                return returnObj;
            }
            if (collectionJSON[priorityAttribute]) {
                returnObj.name = _.escape(collectionJSON[priorityAttribute]);
                returnObj.key = priorityAttribute;
                return returnObj;
            }
            if (collectionJSON.attributes) {
                if (collectionJSON.attributes.name) {
                    returnObj.name = _.escape(collectionJSON.attributes.name);
                    returnObj.key = 'name';
                    return returnObj;
                }
                if (collectionJSON.attributes.qualifiedName) {
                    returnObj.name = _.escape(collectionJSON.attributes.qualifiedName);
                    returnObj.key = 'qualifiedName';
                    return returnObj;
                }
                if (collectionJSON.attributes.id) {
                    if (_.isObject(collectionJSON.attributes.id)) {
                        if (collectionJSON.id.id) {
                            returnObj.name = _.escape(collectionJSON.attributes.id.id);
                        }
                    } else {
                        returnObj.name = _.escape(collectionJSON.attributes.id);
                    }
                    returnObj.key = 'id';
                    return returnObj;
                }
            }
            if (collectionJSON.name) {
                returnObj.name = _.escape(collectionJSON.name);
                returnObj.key = 'name';
                return returnObj;
            }
            if (collectionJSON.qualifiedName) {
                returnObj.name = _.escape(collectionJSON.qualifiedName);
                returnObj.key = 'qualifiedName';
                return returnObj;
            }
            if (collectionJSON.displayText) {
                returnObj.name = _.escape(collectionJSON.displayText);
                returnObj.key = 'displayText';
                return returnObj;
            }
            if (collectionJSON.guid) {
                returnObj.name = _.escape(collectionJSON.guid);
                returnObj.key = 'guid';
                return returnObj;
            }
            if (collectionJSON.id) {
                if (_.isObject(collectionJSON.id)) {
                    if (collectionJSON.id.id) {
                        returnObj.name = _.escape(collectionJSON.id.id);
                    }
                } else {
                    returnObj.name = _.escape(collectionJSON.id);
                }
                returnObj.key = 'id';
                return returnObj;
            }
        }
        returnObj.found = false;
        return returnObj;
    }
    Utils.showTitleLoader = function(loaderEl, titleBoxEl) {
        loaderEl.css({
            'display': 'block',
            'position': 'relative',
            'height': '85px',
            'marginTop': '85px',
            'marginLeft': '50%',
            'left': '0%'
        });
        titleBoxEl.hide();
    }
    Utils.hideTitleLoader = function(loaderEl, titleBoxEl) {
        loaderEl.hide();
        titleBoxEl.fadeIn();
    }
    Utils.findAndMergeRefEntity = function(attributeObject, referredEntities) {
        if (attributeObject && referredEntities) {
            _.each(attributeObject, function(obj, key) {
                if (_.isObject(obj)) {
                    if (_.isArray(obj)) {
                        _.each(obj, function(value) {
                            _.extend(value, referredEntities[value.guid]);
                        });
                    } else {
                        _.extend(obj, referredEntities[obj.guid]);
                    }
                }
            });
        }
    }
    Utils.getNestedSuperTypeObj = function(options) {
        var flag = 0,
            data = options.data,
            collection = options.collection;
        if (options.attrMerge) {
            var attributeDefs = [];
        } else {
            var attributeDefs = {};
        }
        var getData = function(data, collection) {
            if (options.attrMerge) {
                attributeDefs = attributeDefs.concat(data.attributeDefs);
            } else {
                if (attributeDefs[data.name]) {
                    if (_.isArray(attributeDefs[data.name])) {
                        attributeDefs[data.name] = attributeDefs[data.name].concat(data.attributeDefs);
                    } else {
                        _.extend(attributeDefs[data.name], data.attributeDefs);
                    }

                } else {
                    attributeDefs[data.name] = data.attributeDefs;
                }
            }
            if (data.superTypes && data.superTypes.length) {
                _.each(data.superTypes, function(superTypeName) {
                    if (collection.fullCollection) {
                        var collectionData = collection.fullCollection.findWhere({ name: superTypeName }).toJSON();
                    } else {
                        var collectionData = collection.findWhere({ name: superTypeName }).toJSON();
                    }
                    return getData(collectionData, collection);
                });
            }
        }
        getData(data, collection);
        return attributeDefs
    }
    $.fn.toggleAttribute = function(attributeName, firstString, secondString) {
        if (this.attr(attributeName) == firstString) {
            this.attr(attributeName, secondString);
        } else {
            this.attr(attributeName, firstString);
        }
    }
    $('body').on('click', '.expand_collapse_panel', function() {
        var icon = $(this).find('i'),
            panel = $(this).parents('.panel').first(),
            panelBody = panel.find('.panel-body');
        icon.toggleClass('fa-chevron-up fa-chevron-down');
        $(this).toggleAttribute('title', 'Collapse', 'Expand');
        panelBody.toggle();
        $(this).trigger('expand_collapse_panel', [$(this).parents('.panel')]);
    });
    $('body').on('click', '.fullscreen_panel', function() {
        var icon = $(this).find('i'),
            panel = $(this).parents('.panel').first(),
            panelBody = panel.find('.panel-body');
        icon.toggleClass('fa-expand fa-compress');
        $(this).toggleAttribute('title', 'Fullscreen', 'Exit Fullscreen');
        panel.toggleClass('panel-fullscreen');
        panel.find('.expand_collapse_panel').toggle();
        // Condition if user clicks on fullscree button and body is in collapse mode.
        if (panel.hasClass('panel-fullscreen')) {
            $('body').css("position", "fixed");
            if (!panelBody.is(':visible')) {
                panelBody.show();
                panelBody.addClass('full-visible');
            }
            //first show body to get width and height for postion then trigger the event.
            $(this).trigger('fullscreen_done', [$(this).parents('.panel')]);
        } else if (panelBody.hasClass('full-visible')) {
            $('body').removeAttr("style");
            $(this).trigger('fullscreen_done', [$(this).parents('.panel')]);
            //first trigger the event to getwidth and height for postion then hide body.
            panelBody.hide();
            panelBody.removeClass('full-visible');
        } else {
            $('body').removeAttr("style");
            $(this).trigger('fullscreen_done', [$(this).parents('.panel')]);
        }


    });
    return Utils;
});