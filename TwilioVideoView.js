// @flow
import {
    requireNativeComponent,
    View,
    // $FlowFixMe
} from 'react-native';
import { PropTypes } from 'react';

module.exports = requireNativeComponent('RNTwilioVideoView', {
    propTypes: {
        twilioAccessToken: PropTypes.string,
        ...View.propTypes,
    },
});
