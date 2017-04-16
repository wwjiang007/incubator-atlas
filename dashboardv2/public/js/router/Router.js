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

define([
    'jquery',
    'underscore',
    'backbone',
    'App',
    'utils/Globals',
    'utils/Utils',
    'utils/UrlLinks',
    'collection/VTagList'
], function($, _, Backbone, App, Globals, Utils, UrlLinks, VTagList) {
    var AppRouter = Backbone.Router.extend({
        routes: {
            // Define some URL routes
            '': 'defaultAction',
            '!/': 'tagAttributePageLoad',
            '!/tag/tagAttribute/(*name)': 'tagAttributePageLoad',
            '!/taxonomy/detailCatalog/(*url)': 'detailCatalog',
            '!/search/searchResult': 'searchResult',
            '!/detailPage/:id': 'detailPage',
            '!/tag': 'commonAction',
            '!/taxonomy': 'commonAction',
            '!/search': 'commonAction',
            // Default
            '*actions': 'defaultAction'
        },
        initialize: function(options) {
            _.extend(this, _.pick(options, 'entityDefCollection', 'typeHeaders'));
            this.showRegions();
            this.bindCommonEvents();
            this.listenTo(this, 'route', this.postRouteExecute, this);
            this.tagCollection = new VTagList();
            this.searchVent = new Backbone.Wreqr.EventAggregator();
        },
        bindCommonEvents: function() {
            var that = this;
            $('body').on('click', 'li.aboutAtlas', function() {
                that.aboutAtlas();
            });
        },
        aboutAtlas: function() {
            var that = this;
            require([
                'hbs!tmpl/common/aboutAtlas_tmpl',
                'modules/Modal',
                'views/common/aboutAtlas',
            ], function(aboutAtlasTmpl, Modal, aboutAtlasView) {
                var view = new aboutAtlasView();
                var modal = new Modal({
                    title: 'Apache Atlas',
                    content: view,
                    okCloses: true,
                    showFooter: true,
                    allowCancel: false,
                }).open();

                view.on('closeModal', function() {
                    modal.trigger('cancel');
                });

            });
        },
        showRegions: function() {},

        /**
         * @override
         * Execute a route handler with the provided parameters. This is an
         * excellent place to do pre-route setup or post-route cleanup.
         * @param  {Function} callback - route handler
         * @param  {Array}   args - route params
         */
        execute: function(callback, args) {
            this.preRouteExecute();
            if (callback) callback.apply(this, args);
            this.postRouteExecute();
        },
        preRouteExecute: function() {
            // console.log("Pre-Route Change Operations can be performed here !!");
        },
        postRouteExecute: function(name, args) {
            // console.log("Post-Route Change Operations can be performed here !!");
            // console.log("Route changed: ", name);
        },
        detailCatalog: function(url) {
            var that = this;
            require([
                'views/business_catalog/BusinessCatalogHeader',
                'views/business_catalog/BusinessCatalogDetailLayoutView',
                'views/business_catalog/SideNavLayoutView',
                'collection/VCatalogList'
            ], function(BusinessCatalogHeader, BusinessCatalogDetailLayoutView, SideNavLayoutView, VCatalogList) {
                if (Globals.taxonomy) {
                    var paramObj = Utils.getUrlState.getQueryParams();
                    this.collection = new VCatalogList();
                    this.collection.url = url;
                    App.rNHeader.show(new BusinessCatalogHeader({ 'url': url, 'collection': this.collection }));
                    if (!App.rSideNav.currentView) {
                        App.rSideNav.show(new SideNavLayoutView({ 'url': url, 'collection': that.tagCollection, 'typeHeaders': that.typeHeaders }));
                    } else {
                        App.rSideNav.currentView.RBusinessCatalogLayoutView.currentView.manualRender("/" + url);
                        App.rSideNav.currentView.selectTab();
                    }
                    App.rNContent.show(new BusinessCatalogDetailLayoutView({
                        'url': url,
                        'collection': this.collection
                    }));
                    this.collection.fetch({ reset: true });
                } else {
                    that.defaultAction()
                }
            });
        },
        detailPage: function(id) {
            var that = this;
            if (id) {
                require([
                    'views/site/Header',
                    'views/detail_page/DetailPageLayoutView',
                    'views/business_catalog/SideNavLayoutView',
                    'collection/VEntityList'
                ], function(Header, DetailPageLayoutView, SideNavLayoutView, VEntityList) {
                    this.entityCollection = new VEntityList([], {});
                    App.rNHeader.show(new Header());
                    if (!App.rSideNav.currentView) {
                        App.rSideNav.show(new SideNavLayoutView({ 'collection': that.tagCollection, 'typeHeaders': that.typeHeaders }));
                    } else {
                        App.rSideNav.currentView.selectTab();
                    }
                    App.rNContent.show(new DetailPageLayoutView({
                        'collection': this.entityCollection,
                        'id': id,
                        'entityDefCollection': that.entityDefCollection,
                        'typeHeaders': that.typeHeaders
                    }));
                    this.entityCollection.url = UrlLinks.entitiesApiUrl(id);
                    this.entityCollection.fetch({ reset: true });
                });
            }
        },
        tagAttributePageLoad: function(tagName) {
            var that = this;
            require([
                'views/site/Header',
                'views/business_catalog/BusinessCatalogLayoutView',
                'views/business_catalog/SideNavLayoutView',
                'views/tag/TagDetailLayoutView',
            ], function(Header, BusinessCatalogLayoutView, SideNavLayoutView, TagDetailLayoutView) {
                App.rNHeader.show(new Header());
                if (!App.rSideNav.currentView) {
                    App.rSideNav.show(new SideNavLayoutView({
                        'tag': tagName,
                        'typeHeaders': that.typeHeaders,
                        'collection': that.tagCollection
                    }));
                } else {
                    App.rSideNav.currentView.RTagLayoutView.currentView.manualRender(tagName);
                    App.rSideNav.currentView.selectTab();
                }

                if (tagName) {
                    App.rNContent.show(new TagDetailLayoutView({
                        'tag': tagName,
                        'entityDefCollection': that.entityDefCollection,
                        'collection': that.tagCollection,
                        'typeHeaders': that.typeHeaders
                    }));
                }
            });
        },
        commonAction: function() {
            var that = this;
            require([
                'views/site/Header',
                'views/business_catalog/BusinessCatalogLayoutView',
                'views/business_catalog/SideNavLayoutView',
                'views/search/SearchDetailLayoutView',
            ], function(Header, BusinessCatalogLayoutView, SideNavLayoutView, SearchDetailLayoutView) {
                var paramObj = Utils.getUrlState.getQueryParams();
                App.rNHeader.show(new Header());
                if (!App.rSideNav.currentView) {
                    App.rSideNav.show(new SideNavLayoutView({
                        'collection': that.tagCollection,
                        'typeHeaders': that.typeHeaders
                    }));
                } else {
                    App.rSideNav.currentView.selectTab();
                    if (Utils.getUrlState.isTagTab()) {
                        App.rSideNav.currentView.RTagLayoutView.currentView.manualRender();
                    } else if (Utils.getUrlState.isTaxonomyTab()) {
                        App.rSideNav.currentView.RBusinessCatalogLayoutView.currentView.manualRender(undefined, true);
                    }
                }
                if (Globals.entityCreate && Utils.getUrlState.isSearchTab()) {
                    App.rNContent.show(new SearchDetailLayoutView({
                        'value': paramObj,
                        'entityDefCollection': that.entityDefCollection,
                        'initialView': true,
                        'typeHeaders': that.typeHeaders
                    }))
                } else {
                    App.rNContent.$el.html("");
                    App.rNContent.destroy();
                }
            });
        },
        searchResult: function() {
            var that = this;
            require([
                'views/site/Header',
                'views/business_catalog/BusinessCatalogLayoutView',
                'views/business_catalog/SideNavLayoutView',
                'views/search/SearchDetailLayoutView'
            ], function(Header, BusinessCatalogLayoutView, SideNavLayoutView, SearchDetailLayoutView) {
                var paramObj = Utils.getUrlState.getQueryParams();
                App.rNHeader.show(new Header());
                if (!App.rSideNav.currentView) {
                    App.rSideNav.show(new SideNavLayoutView({
                        'value': paramObj,
                        'collection': that.tagCollection,
                        'searchVent': that.searchVent,
                        'typeHeaders': that.typeHeaders
                    }));
                } else {
                    App.rSideNav.currentView.RSearchLayoutView.currentView.manualRender(paramObj);
                }
                App.rSideNav.currentView.selectTab();
                App.rNContent.show(new SearchDetailLayoutView({
                    'value': paramObj,
                    'entityDefCollection': that.entityDefCollection,
                    'typeHeaders': that.typeHeaders,
                    'searchVent': that.searchVent,
                    'initialView': (paramObj.type || (paramObj.dslChecked == "true" ? "" : paramObj.tag) || (paramObj.query ? paramObj.query.trim() : "")).length === 0
                }));
            });
        },
        defaultAction: function(actions) {
            // We have no matching route, lets just log what the URL was
            Utils.setUrl({
                url: '#!/search',
                mergeBrowserUrl: false,
                updateTabState: function() {
                    return { searchUrl: this.url, stateChanged: false };
                },
                trigger: true
            });

            console.log('No route:', actions);
        }
    });
    return AppRouter;
});
