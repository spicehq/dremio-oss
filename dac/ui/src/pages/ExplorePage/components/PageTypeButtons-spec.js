/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { shallow } from "enzyme";
import Immutable from "immutable";
import { PageTypeButtonsView } from "@app/pages/ExplorePage/components/PageTypeButtons";
import { PageTypes } from "@app/pages/ExplorePage/pageTypes";

describe("PageTypeButtonsView", () => {
  let commonProps;
  beforeEach(() => {
    commonProps = {
      showWiki: true,
      dataset: Immutable.Map({
        isNewQuery: false,
        apiLinks: {
          namespaceEntity: "/home/%40dremio/dataset/testName",
        },
      }),
      location: {
        pathname: "/space",
      },
    };
  });

  it("should render Wiki button if showWiki = true", () => {
    const wrapper = shallow(<PageTypeButtonsView {...commonProps} />);
    expect(wrapper.find({ pageType: PageTypes.wiki })).to.have.length(1); // wiki button is rendered
  });

  it("should not render wiki button if showWiki = false", () => {
    // common props does not contain entity id
    const props = {
      ...commonProps,
      showWiki: false,
    };
    const wrapper = shallow(<PageTypeButtonsView {...props} />);
    expect(wrapper.find({ pageType: PageTypes.wiki })).to.have.length(0); // wiki button is not rendered
  });
});
