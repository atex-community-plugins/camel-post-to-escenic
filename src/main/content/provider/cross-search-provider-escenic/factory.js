atex.onecms.register('ng-factory', 'CrossSearchProviderEscenic', function () {
  return ['CrossSearchResult', '$q', '$http', '$sce', 'ApplicationConfigurationService', 'UserProfileService', 'ContentService',
    'AuthenticationService', 'FileService', 'PolopolyFSFileService', 'TypeService', 'ADMUtils',
    function (CrossSearchResult, $q, $http, $sce, ApplicationConfigurationService, UserProfileService, ContentService,
              AuthenticationService, FileService, PolopolyFSFileService, TypeService, ADMUtils) {
      // CFG
      var providerName = "Escenic";
      var searchRow = 10;
      var customCopy = false;
      var useRemote = false;
      var facetOptions = {};
      var mapFieldName = {};
      var extraFilters = [];
      var sortOptions = [
        {
          "label": "Creation Date ↓",
          "value": "%2Bmeta-orderby%3A\"creationdate descending\""
        },
        {
          "label": "Relevancy ↓",
          "value": "%2Bmeta-orderby%3A\"score descending\""
        },
        {
          "label": "Published Date ↓",
          "value": "%2Bmeta-orderby%3A\"activepublishdate descending\" %2Bmeta-orderby%3A\"lastmodifieddate descending\""
        },
        {
          "label": "Alphabetically ↓",
          "value": "%2Bmeta-orderby%3A\"title_string descending\""
        }

      ];
      // var defaultSort = "originalCreationTime_dt desc";
      var defaultSort = "%2Bmeta-orderby%3A\"score descending\"";
      var statusList = [];
      var webStatusList = [];
      var queryTermsInAnd = false;

      // END CFG

      // PRIVATE
      function formatEscenicItems(doc, previewUrl) {
        var result = new CrossSearchResult('Escenic');
        result.id = doc.id;
        result.desc = atex.onecms.ObjectUtils.getByPath(doc, 'summary/summary') || "";

        var links = doc.link;
        if (links) {
          links.forEach(function (link) {
            if (link) {
              if (link['rel'] !== undefined) {

                if (link['rel'] === 'thumbnail') {
                  result.thumbUrl = link['href'];
                }

                if (link['rel'] === 'http://www.escenic.com/types/relation/summary-model') {
                  result.type = getType(link['title']);
                  result._type = _getType(result.type);
                }

                if (link['rel'] === 'self') {
                  result.id = link['href'];
                }
              }
            }
          });
        }

        result.name = atex.onecms.ObjectUtils.getByPath(doc, 'title/title') || "";

        result.ref = "<a href='" + previewUrl + "openPreview?escenicLocation=" + result.id + "' target='_blank'>Preview on site</a>";

        return result;
      }

      function _getType(type) {
        if (type) {
          switch (type) {
            case "Image" :
              return "atex.onecms.escenic.image";
            case "Article" :
              return "atex.onecms.escenic.article";
            case "Video" :
              return "atex.onecms.escenic.video";
            case "Collection" :
              return "atex.onecms.escenic.collection";
          }
        }
      }

      function getType(escenicType) {
        if (escenicType) {
          switch(escenicType) {
            case "picture" :
              return "Image";
            case "news" :
              return "Article";
            case "video" :
              return "Video";
            case "gallery" :
              return "Gallery";
            case "code" :
              return "Code";
            case "socialembed" :
              return "Social Embed";
            case "soundcloud" :
              return "Sound Cloud";
            case "com.escenic.person" :
              return "Person";
          }
        }
      }

      function getDateLabel(escenicLabel) {
        if (escenicLabel) {
          switch(escenicLabel) {
            case "1" :
              return "Since yesterday";
            case "7" :
              return "Since last week";
            case "31" :
              return "Since last month";
            case "365" :
              return "Since 365 days ago";
          }
        }
      }

      // FaceResult class
      function FacetResult(fieldName, label, value, Score, isDate, gap, selected, fieldLabel) {
        this.fieldName = fieldName;
        this.fieldLabel = fieldLabel ? fieldLabel : this.fieldName;
        this.value = value;
        this.score = Score;
        this.isDate = isDate;
        this.gap = gap;
        this.selected = selected;
        this.label = label;

        var uniqueKey = this.value;
        /*
         * this is needed because in case of a date object the timestamp will
         * change as far as the user clicks on an item and re-does the query
         */
        if (isDate && value) {
          var start = this.value.start;
          var end = this.value.end;
          uniqueKey = start.substring(0, start.indexOf('T')) + "TO" +  end.substring(0, end.indexOf('T'));
        }
        this.identifier = uniqueKey;
      }

      function extractDate(filterQuery, startOrEnd) {
        var res = undefined;
        if (startOrEnd === 0) {
           res = filterQuery.substring(filterQuery.indexOf('start:')+ 'start:'.length, filterQuery.indexOf('end:'));
        } else if (startOrEnd === 1) {
           res = filterQuery.substring(filterQuery.indexOf('end:')+ 'end:'.length);
        }
        return res;
      }

      function cleanupFilterQuery(filterQuery, that) {
        var res = filterQuery.split(";");
        var finalQuery = '';
        if (res) {
          for(var i=0; i < res.length; i++) {
              var pair = res[i].split("@@");
              if (pair && pair.length > 1) {
                if (pair[0] === 'creationdate') {
                  that.startDate = extractDate(pair[1], 0);
                  that.endDate = extractDate(pair[1], 1);
                } else {
                  finalQuery += " " + pair[1];
                }
              }
          }
        }
        return finalQuery;
      }

      // END PRIVATE

      function CrossSearchProviderEscenic() {
        this.clear();
        // The ID (must be unique) of this provider
        this.id = 'Escenic';
        this.name = providerName;
        this.searchRows = searchRow;
        this.hasFaceting = true;
        this.hasAssignedToMe = false;
        this.extraFilters = [];
        this.sortOptions = sortOptions;
      }

      CrossSearchProviderEscenic.prototype.clear = function () {
        this.resetFaceting();
        this.results = [];
        this.numFound = 0;
        this.startDate = undefined;
        this.endDate = undefined;
        this.assignedToMe = false;
        this.filterByMe = undefined;
        this.extraFilter = undefined;
        this.selectedFilter = undefined;
        this.pageNumber = undefined;
        this.sort = defaultSort;
        if (this.sort) {
          this.selectedSort = sortOptions.filter(function (sort) {
            return sort.value === defaultSort;
          })[0];
        }
      };

      CrossSearchProviderEscenic.prototype.resetFaceting = function () {
        this.facets = [];
        this.facetFilterQuery = {};
        this.startDate = undefined;
        this.endDate = undefined;
        this.lastSearch = "";
      };

      CrossSearchProviderEscenic.prototype.faceting = function (searchResponseData) {
        this.facets.length = 0;
        this.numFound = 0;

        for (var i=0; i < searchResponseData.group.length; i++) {
            var facet = searchResponseData.group[i];

            var fieldFacets = [];
            fieldFacets.tick = 10;
            var fieldName = facet.title;
            if (facet.hasOwnProperty('query')) {

              var queries = facet.query;
              for (var j = 0; j < queries.length; j++) {
                var query = queries[j];
                var role = query['role'];
                var count = query['totalResults'];
                var searchTerms = query['searchTerms'];
                var label = undefined;
                var value = undefined;
                var isDate = false;
                if (fieldName && fieldName === 'creationdate') {
                  isDate = true;
                  label = query['title'];
                  label = getDateLabel(label);
                  var start = query['start'];
                  var end = query['end'];
                  value = {};
                  value.start = start;
                  value.end = end;

                } else {
                  label = query['title'];
                  value = query['related'];
                }

                var facet = new FacetResult(fieldName, label, value, count, isDate, null, false, mapFieldName[fieldName]);
                fieldFacets.push(facet);
              }
              if (!fieldFacets.tick) {
                fieldFacets.tick = fieldFacets.length;
              }
              this.facets.push(fieldFacets);
            }
          }
      };

      CrossSearchProviderEscenic.prototype.getFilterQueryString = function () {
        var filterQueryString = '';

        for (var fieldCode in this.facetFilterQuery) {
          var fieldName = fieldCode.substring(0, fieldCode.indexOf('@@'));
          var facetField = this.facetFilterQuery[fieldCode];
          filterQueryString += fieldName;
          if (facetField.isDate) {
            filterQueryString += '@@start:' + facetField.value.start + "end:" + facetField.value.end + ";";
          } else {
            filterQueryString += "@@" + facetField.value + ";";
          }
        }

        return filterQueryString;

      };

      CrossSearchProviderEscenic.prototype.facetByItem = function (fieldName, item, action) {
        if (action === 'remove') {
          delete this.facetFilterQuery[fieldName + '@@' + item.identifier];
        } else {
          this.facetFilterQuery[fieldName + "@@" + item.identifier] = (item);
        }
        return this.search(this.lastSearch, 0, true);
      };

      CrossSearchProviderEscenic.prototype.toggleAssignedToMe = function (userName) {
        return this.search(this.lastSearch, 0, true);
      };

      CrossSearchProviderEscenic.prototype.toggleExtraFilter = function (extra) {
        return this.search(this.lastSearch, 0, true);
      };

      CrossSearchProviderEscenic.prototype.toggleSort = function (sort) {

        if (sort && sort.trim()) {
          this.sort = sort;
        } else {
          this.sort = undefined;
        }
        return this.search(this.lastSearch, 0, true);

      };

      CrossSearchProviderEscenic.prototype.search = function (queryStr, start, keepFacet) {
        var that = this;
        that.numFound = 0;

        //reset the creationdate facet - this will be reset when processing filterQuery
        that.startDate = undefined;
        that.endDate = undefined;

        if (start === 0) {
          that.results = [];
        }
        var facetFilter;
        if (keepFacet) {
          // Reuse last facet
          facetFilter = that.getFilterQueryString();
        } else {
          that.resetFaceting();
        }
        that.lastSearch = queryStr;
        //escenic search
        var transferInfo = {
          'application': 'act',
          'widget': 'escenic-cross-search-provider'
        };

          return UserProfileService.widgetConfiguration(transferInfo).then(function (widgetCfg) {

          var escenicUrl = atex.onecms.ObjectUtils.getByPath(widgetCfg, "url");
          var url = escenicUrl + "search?query=" + queryStr;
          //calculate the page number
          var pageNumber = (start + 50) / 50;
          url += "&pageNumber=" + pageNumber;

          if (facetFilter !== undefined) {
            url += "&filterQuery=" + cleanupFilterQuery(facetFilter, that).trim();

          }

          if (that.startDate && that.endDate) {
            url += "&start=" + that.startDate.trim() + "&end=" + that.endDate.trim();
          }

          if (that.sort) {
            url += "&sort=" + that.sort;
          }

          return $http.get(url).then(function (res) {
            that.faceting(res.data);
            var promises = res.data.entry.map(function (doc) {
              return formatEscenicItems(doc, escenicUrl);
            });
            return $q.all(promises).then(function (docs) {
              that.numFound = res.data.totalResults;
              var filteredEscenicDocuments = docs
              Array.prototype.push.apply(that.results, filteredEscenicDocuments);
              return filteredEscenicDocuments;
            });
          }).then(null, function (error) {
            console.log('error', error);
          });
        });
      };

      CrossSearchProviderEscenic.prototype.getContentToPutInClipboard = function (entry) {
        console.log("getContent");

        var transferInfo = {
          'application': 'act',
          'widget': 'escenic-cross-search-provider'
        };

       return UserProfileService.widgetConfiguration(transferInfo).then(function (widgetCfg) {

          var escenicUrl = atex.onecms.ObjectUtils.getByPath(widgetCfg, "url");
          escenicUrl += "getContent?escenicLocation=" + entry.id;

          return $http.get(escenicUrl).then(function (res) {
            if (res !== undefined) {
              return [{
                type: "atex.onecms.external.reference",
                id: res.data.id
              }];
            }

          }).then(null, function (error) {
            console.log('error', error);
          });

        })
      };

      CrossSearchProviderEscenic.prototype.getIconByEntryType = function () {
        return undefined;
      };

      CrossSearchProviderEscenic.prototype.isEnabled = function() {
        var transferInfo = {
          'application': 'act',
          'widget': 'escenic-cross-search-provider'
        };

        return UserProfileService.widgetConfiguration(transferInfo).then(function (widgetCfg) {
          return !atex.onecms.ObjectUtils.getByPath(widgetCfg, "disable");
        });
      };

      // Return the constructor function
      return CrossSearchProviderEscenic;
    }
  ]
});
