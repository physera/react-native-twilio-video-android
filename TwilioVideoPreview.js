import {
  requireNativeComponent,
} from 'react-native';
import React from 'react';

const propTypes = {
  ...View.propTypes,
};

class TwilioVideoPreview extends React.Component {
  render() {
    return (
      <NativeTwilioVideoPreview {...this.props} />
    );
  }
}

TwilioVideoPreview.propTypes = propTypes;

const NativeTwilioVideoPreview = requireNativeComponent(
  'RNTwilioVideoPreview',
  TwilioVideoPreview
);

module.exports = TwilioVideoPreview;
